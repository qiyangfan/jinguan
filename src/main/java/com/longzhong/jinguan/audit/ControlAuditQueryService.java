package com.longzhong.jinguan.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 审计查询与导出:参数化筛选,导出限行并脱敏(摘要已是脱敏 JSON)。 */
@Service
public class ControlAuditQueryService {

  static final int MAX_PAGE_SIZE = 200;
  static final int EXPORT_LIMIT = 10_000;

  private final JdbcTemplate jdbc;

  public ControlAuditQueryService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 查询条件;空值不过滤。 */
  public record Filter(
      String actor, String action, String targetType, String result, Instant from, Instant to) {}

  /** 审计行视图。 */
  public record AuditView(
      String id,
      Instant occurredAt,
      String actor,
      String actorKind,
      String actorRole,
      String action,
      String targetType,
      String targetId,
      String tenantKey,
      String reason,
      String summaryJson,
      String result,
      String requestId) {}

  @Transactional(readOnly = true)
  public List<AuditView> search(Filter filter, int page, int size) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
    List<Object> args = new ArrayList<>();
    String where = whereClause(filter, args);
    args.add(safeSize);
    args.add(safePage * safeSize);
    return jdbc.query(
        "SELECT * FROM control_audit_log"
            + where
            + " ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?",
        (rs, rowNum) ->
            new AuditView(
                rs.getString("id"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("actor"),
                rs.getString("actor_kind"),
                rs.getString("actor_role"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                rs.getString("tenant_key"),
                rs.getString("reason"),
                rs.getString("summary_json"),
                rs.getString("result"),
                rs.getString("request_id")),
        args.toArray());
  }

  @Transactional(readOnly = true)
  public String exportCsv(Filter filter) {
    List<Object> args = new ArrayList<>();
    String where = whereClause(filter, args);
    args.add(EXPORT_LIMIT);
    StringBuilder csv =
        new StringBuilder(
            "occurred_at,actor,actor_kind,actor_role,action,target_type,target_id,"
                + "tenant_key,result,request_id,reason,summary\n");
    jdbc.query(
        "SELECT * FROM control_audit_log" + where + " ORDER BY occurred_at DESC, id DESC LIMIT ?",
        rs -> {
          csv.append(csv(rs.getTimestamp("occurred_at").toInstant().toString()))
              .append(',')
              .append(csv(rs.getString("actor")))
              .append(',')
              .append(csv(rs.getString("actor_kind")))
              .append(',')
              .append(csv(rs.getString("actor_role")))
              .append(',')
              .append(csv(rs.getString("action")))
              .append(',')
              .append(csv(rs.getString("target_type")))
              .append(',')
              .append(csv(rs.getString("target_id")))
              .append(',')
              .append(csv(rs.getString("tenant_key")))
              .append(',')
              .append(csv(rs.getString("result")))
              .append(',')
              .append(csv(rs.getString("request_id")))
              .append(',')
              .append(csv(rs.getString("reason")))
              .append(',')
              .append(csv(rs.getString("summary_json")))
              .append('\n');
        },
        args.toArray());
    return csv.toString();
  }

  private static String whereClause(Filter filter, List<Object> args) {
    StringBuilder where = new StringBuilder();
    appendCondition(where, args, "actor = ?", filter.actor());
    appendCondition(where, args, "action = ?", filter.action());
    appendCondition(where, args, "target_type = ?", filter.targetType());
    appendCondition(where, args, "result = ?", filter.result());
    if (filter.from() != null) {
      appendCondition(where, args, "occurred_at >= ?", java.sql.Timestamp.from(filter.from()));
    }
    if (filter.to() != null) {
      appendCondition(where, args, "occurred_at <= ?", java.sql.Timestamp.from(filter.to()));
    }
    return where.toString();
  }

  private static void appendCondition(
      StringBuilder where, List<Object> args, String clause, Object value) {
    if (value == null) {
      return;
    }
    where.append(where.isEmpty() ? " WHERE " : " AND ").append(clause);
    args.add(value);
  }

  private static String csv(String value) {
    if (value == null) {
      return "";
    }
    return '"' + value.replace("\"", "\"\"") + '"';
  }
}
