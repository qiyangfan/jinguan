package com.longzhong.jinguan.command;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 命令超时与保留清理:领取超时重试或进死信;只清终态。 */
@Service
public class CommandMaintenanceService {

  private final ControlCommandRepository commands;
  private final AuditRecorder recorder;
  private final int claimTimeoutSeconds;

  public CommandMaintenanceService(
      ControlCommandRepository commands,
      AuditRecorder recorder,
      @Value("${jinguan.commands.claim-timeout-seconds:300}") int claimTimeoutSeconds) {
    this.commands = commands;
    this.recorder = recorder;
    this.claimTimeoutSeconds = claimTimeoutSeconds;
  }

  /** 领取超时:未达上限回 PENDING 重试,达到上限进死信。 */
  @Transactional
  public Map<String, Integer> sweepTimeouts() {
    Instant cutoff = Instant.now().minusSeconds(claimTimeoutSeconds);
    List<ControlCommandEntity> claimed =
        commands.findByStatusAndClaimedAtBefore(CommandStatus.CLAIMED, cutoff);
    int retried = 0;
    int deadLettered = 0;
    for (ControlCommandEntity command : claimed) {
      if (command.getAttempts() >= command.getMaxAttempts()) {
        command.markDeadLetter("领取超时未回执,已达重试上限");
        commands.save(command);
        recorder.record(
            AuditActor.system(),
            AuditActions.COMMAND_DEAD_LETTER,
            "command",
            command.getId(),
            null,
            null,
            Map.of("action", command.getAction(), "attempts", command.getAttempts()),
            "FAILED");
        deadLettered++;
      } else {
        command.retry();
        commands.save(command);
        retried++;
      }
    }
    return Map.of("retried", retried, "deadLettered", deadLettered);
  }

  /** 只清理终态命令;未 ack/pending 永不删除。 */
  @Transactional
  public int purgeFinished(Instant cutoff) {
    List<ControlCommandEntity> expired =
        commands.findByStatusInAndFinishedAtBefore(CommandStatus.TERMINAL, cutoff);
    if (expired.isEmpty()) {
      return 0;
    }
    commands.deleteAll(expired);
    recorder.record(
        AuditActor.system(),
        AuditActions.COMMAND_PURGE,
        "command",
        null,
        null,
        "终态命令保留期清理",
        Map.of("purged", expired.size(), "cutoff", cutoff.toString()),
        "SUCCESS");
    return expired.size();
  }
}
