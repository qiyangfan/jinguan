package com.longzhong.jinguan.alerts;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 告警投递记录保留清理:只清理终态记录,清理留审计。 */
@Service
public class AlertRetentionService {

  private final AlertEventRepository repository;
  private final AuditRecorder recorder;

  public AlertRetentionService(AlertEventRepository repository, AuditRecorder recorder) {
    this.repository = repository;
    this.recorder = recorder;
  }

  @Transactional
  public int purgeTerminal(Instant cutoff) {
    List<AlertEventEntity> expired =
        repository.findByStatusInAndCreatedAtBefore(
            List.of("DELIVERED", "FAILED", "ACKED"), cutoff);
    if (expired.isEmpty()) {
      return 0;
    }
    repository.deleteAll(expired);
    recorder.record(
        AuditActor.system(),
        AuditActions.ALERT_PURGE,
        "alert",
        null,
        null,
        "告警记录保留期清理",
        Map.of("purged", expired.size(), "cutoff", cutoff.toString()),
        "SUCCESS");
    return expired.size();
  }
}
