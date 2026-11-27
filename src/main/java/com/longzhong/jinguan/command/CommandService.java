package com.longzhong.jinguan.command;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import com.longzhong.jinguan.serviceidentity.ServiceAuthService;
import com.longzhong.jinguan.serviceidentity.ServicePrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** 命令提交与领取:拒绝任意 SQL 与未知 action;回执见 CommandAckService。 */
@Service
public class CommandService {

  private static final String UNKNOWN_SERVICE = "serviceId 不在服务身份目录: ";
  private static final int MAX_KEY_LENGTH = 128;

  private final ControlCommandRepository commands;
  private final CommandActionCatalog catalog;
  private final ServiceAuthService serviceAuth;
  private final CommandViewMapper views;
  private final AuditRecorder recorder;
  private final int defaultMaxAttempts;

  public CommandService(
      ControlCommandRepository commands,
      CommandActionCatalog catalog,
      ServiceAuthService serviceAuth,
      CommandViewMapper views,
      AuditRecorder recorder,
      @Value("${jinguan.commands.max-attempts:3}") int defaultMaxAttempts) {
    this.commands = commands;
    this.catalog = catalog;
    this.serviceAuth = serviceAuth;
    this.views = views;
    this.recorder = recorder;
    this.defaultMaxAttempts = defaultMaxAttempts;
  }

  /** 命令视图;params/result 为 JSON 节点。 */
  public record CommandView(
      String id,
      String serviceId,
      String action,
      int actionSchemaVersion,
      JsonNode params,
      String reason,
      String actor,
      String status,
      int attempts,
      int maxAttempts,
      String claimedBy,
      JsonNode result,
      String error,
      Instant createdAt,
      Instant updatedAt,
      Instant finishedAt) {}

  /** 提交结果:重复幂等键返回既有命令。 */
  public record SubmitResult(CommandView command, boolean replayed) {}

  @SuppressWarnings("PMD.UseObjectForClearerAPI") // 提交字段均为必需契约值,容器对象只增样板
  @Transactional
  public SubmitResult submit(
      String serviceId,
      String action,
      int actionSchemaVersion,
      JsonNode params,
      String reason,
      String actorSubject,
      String idempotencyKey) {
    requireReason(reason);
    requireKnownService(serviceId);
    catalog.validate(action, actionSchemaVersion, params);
    String key =
        idempotencyKey == null || idempotencyKey.isBlank()
            ? UUID.randomUUID().toString()
            : idempotencyKey.trim();
    if (key.length() > MAX_KEY_LENGTH) {
      throw new IllegalArgumentException("idempotencyKey 不超过 128 字符");
    }
    var existing = commands.findByIdempotencyKey(key);
    if (existing.isPresent()) {
      return new SubmitResult(views.viewOf(existing.get()), true);
    }
    String paramsJson = params == null ? "{}" : params.toString();
    ControlCommandEntity command =
        commands.save(
            ControlCommandEntity.pending(
                serviceId,
                action,
                actionSchemaVersion,
                paramsJson,
                reason,
                actorSubject,
                key,
                defaultMaxAttempts));
    recorder.record(
        actorOf(actorSubject),
        AuditActions.COMMAND_SUBMIT,
        "command",
        command.getId(),
        null,
        reason,
        Map.of(
            "serviceId", serviceId,
            "action", action,
            "actionSchemaVersion", actionSchemaVersion,
            "idempotencyKey", key),
        "SUCCESS");
    return new SubmitResult(views.viewOf(command), false);
  }

  /** 服务领取命令;每个实例领取后 attempts+1。 */
  @Transactional
  public List<CommandView> claim(ServicePrincipal principal, int limit) {
    int safeLimit = Math.min(Math.max(limit, 1), 50);
    List<ControlCommandEntity> pending =
        commands.findByServiceIdAndStatusOrderByCreatedAtAsc(
            principal.serviceId(), CommandStatus.PENDING, Limit.of(safeLimit));
    List<CommandView> claimed = new ArrayList<>();
    for (ControlCommandEntity command : pending) {
      command.claim(principal.clientId());
      ControlCommandEntity saved = commands.save(command);
      recorder.record(
          AuditActor.service(principal.serviceId()),
          AuditActions.COMMAND_CLAIM,
          "command",
          saved.getId(),
          null,
          null,
          Map.of("action", saved.getAction(), "attempts", saved.getAttempts()),
          "SUCCESS");
      claimed.add(views.viewOf(saved));
    }
    return claimed;
  }

  private void requireKnownService(String serviceId) {
    if (serviceId == null || serviceId.isBlank()) {
      throw new IllegalArgumentException("serviceId 必填");
    }
    Set<String> serviceIds =
        serviceAuth.directory().stream()
            .map(ServiceAuthService.DirectoryEntry::serviceId)
            .collect(Collectors.toSet());
    if (!serviceIds.contains(serviceId)) {
      throw new IllegalArgumentException(UNKNOWN_SERVICE + serviceId);
    }
  }

  private static void requireReason(String reason) {
    if (reason == null || reason.isBlank() || reason.length() > 512) {
      throw new IllegalArgumentException("变更原因必填且不超过 512 字符");
    }
  }

  private static AuditActor actorOf(String actorSubject) {
    return actorSubject == null || actorSubject.isBlank()
        ? AuditActor.system()
        : AuditActor.user(actorSubject, "ADMIN");
  }
}
