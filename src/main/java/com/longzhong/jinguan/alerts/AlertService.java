package com.longzhong.jinguan.alerts;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import com.longzhong.jinguan.common.Redaction;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 告警受理与确认;投递由独立任务异步完成,不阻塞控制写。 */
@Service
public class AlertService {

  private static final Logger log = LoggerFactory.getLogger(AlertService.class);

  private final AlertEventRepository repository;
  private final AuditRecorder recorder;
  private final ObjectMapper mapper;
  private final AlertProperties properties;

  public AlertService(
      AlertEventRepository repository,
      AuditRecorder recorder,
      ObjectMapper mapper,
      AlertProperties properties) {
    this.repository = repository;
    this.recorder = recorder;
    this.mapper = mapper;
    this.properties = properties;
  }

  /** 告警视图。 */
  public record AlertView(
      String id,
      String severity,
      String source,
      String type,
      String tenantKey,
      String serviceId,
      String dedupKey,
      Instant observedAt,
      String payloadJson,
      String runbook,
      String status,
      int attempts,
      String lastError,
      Instant createdAt,
      Instant deliveredAt,
      Instant ackedAt,
      String ackedBy) {}

  /** 去重窗口内同 dedup_key 只保留一条;返回是否新建。 */
  @SuppressWarnings("PMD.UseObjectForClearerAPI") // 告警字段均为独立必填维度,容器对象只增样板
  @Transactional
  public boolean raise(
      String type,
      String severity,
      String source,
      String tenantKey,
      String serviceId,
      String dedupKey,
      Map<String, Object> payload,
      String runbook) {
    Instant windowStart = Instant.now().minusSeconds(properties.dedupWindowSeconds());
    if (repository.existsByDedupKeyAndCreatedAtAfter(dedupKey, windowStart)) {
      return false;
    }
    String payloadJson = serialize(Redaction.redact(payload == null ? Map.of() : payload));
    repository.save(
        AlertEventEntity.pending(
            UUID.randomUUID().toString(),
            severity,
            source,
            type,
            tenantKey,
            serviceId,
            dedupKey,
            Instant.now(),
            payloadJson,
            runbook));
    recorder.record(
        AuditActor.system(),
        AuditActions.ALERT_RAISE,
        "alert",
        dedupKey,
        tenantKey,
        null,
        Map.of("type", type, "severity", severity, "source", source),
        "SUCCESS");
    log.warn("告警入队 type={} severity={} source={} dedup={}", type, severity, source, dedupKey);
    return true;
  }

  @Transactional
  public void ack(String id, String actor) {
    AlertEventEntity event =
        repository
            .findById(id)
            .orElseThrow(() -> new com.longzhong.jinguan.common.NotFoundException("告警不存在: " + id));
    event.ack(actor);
    repository.save(event);
    recorder.record(
        AuditActor.user(actor, null),
        AuditActions.ALERT_ACK,
        "alert",
        id,
        event.getTenantKey(),
        "人工确认",
        Map.of("type", event.getType()),
        "SUCCESS");
  }

  @Transactional(readOnly = true)
  public List<AlertView> list(int limit) {
    return repository
        .findAllByOrderByCreatedAtDesc(Limit.of(Math.min(Math.max(limit, 1), 200)))
        .stream()
        .map(AlertService::viewOf)
        .toList();
  }

  private static AlertView viewOf(AlertEventEntity entity) {
    return new AlertView(
        entity.getId(),
        entity.getSeverity(),
        entity.getSource(),
        entity.getType(),
        entity.getTenantKey(),
        entity.getServiceId(),
        entity.getDedupKey(),
        entity.getObservedAt(),
        entity.getPayloadJson(),
        entity.getRunbook(),
        entity.getStatus(),
        entity.getAttempts(),
        entity.getLastError(),
        entity.getCreatedAt(),
        entity.getDeliveredAt(),
        entity.getAckedAt(),
        entity.getAckedBy());
  }

  private String serialize(Map<String, Object> payload) {
    try {
      return mapper.writeValueAsString(payload);
    } catch (JacksonException e) {
      return "{}";
    }
  }
}
