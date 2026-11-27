package com.longzhong.jinguan.command;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import com.longzhong.jinguan.common.ConflictException;
import com.longzhong.jinguan.common.ForbiddenException;
import com.longzhong.jinguan.common.NotFoundException;
import com.longzhong.jinguan.serviceidentity.ServicePrincipal;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** 命令回执:服务身份绑定、状态机约束与审计。 */
@Service
public class CommandAckService {

  private final ControlCommandRepository commands;
  private final CommandViewMapper views;
  private final AuditRecorder recorder;

  public CommandAckService(
      ControlCommandRepository commands, CommandViewMapper views, AuditRecorder recorder) {
    this.commands = commands;
    this.views = views;
    this.recorder = recorder;
  }

  /** 服务回执:SUCCEEDED/FAILED;服务身份必须与命令目标一致。 */
  @Transactional
  public CommandService.CommandView ack(
      ServicePrincipal principal, String id, String status, JsonNode result, String error) {
    String normalized = normalizeAckStatus(status);
    ControlCommandEntity command = requireAckable(principal, id);
    if (CommandStatus.SUCCEEDED.equals(normalized)) {
      command.markSucceeded(result == null ? "{}" : result.toString());
    } else {
      requireError(error);
      command.markFailed(error);
    }
    ControlCommandEntity saved = commands.save(command);
    recorder.record(
        AuditActor.service(principal.serviceId()),
        AuditActions.COMMAND_ACK,
        "command",
        id,
        null,
        error,
        Map.of("status", saved.getStatus(), "action", saved.getAction()),
        CommandStatus.SUCCEEDED.equals(normalized) ? "SUCCESS" : "FAILED");
    return views.viewOf(saved);
  }

  private ControlCommandEntity requireAckable(ServicePrincipal principal, String id) {
    ControlCommandEntity command =
        commands.findById(id).orElseThrow(() -> new NotFoundException("命令不存在: " + id));
    if (!command.getServiceId().equals(principal.serviceId())) {
      throw new ForbiddenException("服务身份与命令目标不一致");
    }
    if (!CommandStatus.CLAIMED.equals(command.getStatus())) {
      throw new ConflictException("命令状态不允许回执: " + command.getStatus());
    }
    return command;
  }

  private static String normalizeAckStatus(String status) {
    String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    if (!CommandStatus.SUCCEEDED.equals(normalized) && !CommandStatus.FAILED.equals(normalized)) {
      throw new IllegalArgumentException("回执状态只能是 SUCCEEDED/FAILED");
    }
    return normalized;
  }

  private static void requireError(String error) {
    if (error == null || error.isBlank()) {
      throw new IllegalArgumentException("失败回执必须提供 error");
    }
  }
}
