package com.longzhong.jinguan.audit;

import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 审计保留:在线超过保留期的行先归档到冷存表再删除;删除是本仓唯一被允许的审计清理路径。 */
@Service
public class AuditRetentionService {

  private static final Logger log = LoggerFactory.getLogger(AuditRetentionService.class);

  private final JdbcTemplate jdbc;
  private final AuditRecorder recorder;

  public AuditRetentionService(JdbcTemplate jdbc, AuditRecorder recorder) {
    this.jdbc = jdbc;
    this.recorder = recorder;
  }

  /** 归档并清理早于 cutoff 的在线审计;返回 {archived, purged}。 */
  @Transactional
  public Map<String, Integer> archiveAndPurge(Instant cutoff) {
    int archived =
        jdbc.update(
            """
            INSERT INTO control_audit_archive
              (id, occurred_at, actor, actor_kind, actor_role, action, target_type, target_id,
               tenant_key, reason, summary_json, result, request_id, archived_at)
            SELECT id, occurred_at, actor, actor_kind, actor_role, action, target_type, target_id,
                   tenant_key, reason, summary_json, result, request_id, ?
            FROM control_audit_log WHERE occurred_at < ?
            """,
            java.sql.Timestamp.from(Instant.now()),
            java.sql.Timestamp.from(cutoff));
    int purged =
        jdbc.update(
            "DELETE FROM control_audit_log WHERE occurred_at < ?", java.sql.Timestamp.from(cutoff));
    if (archived > 0 || purged > 0) {
      log.info("审计归档清理 archived={} purged={} cutoff={}", archived, purged, cutoff);
      recorder.record(
          AuditActor.system(),
          AuditActions.RETENTION_CLEANUP,
          "control_audit",
          null,
          null,
          "保留期归档清理",
          Map.of("archived", archived, "purged", purged, "cutoff", cutoff.toString()),
          "SUCCESS");
    }
    return Map.of("archived", archived, "purged", purged);
  }
}
