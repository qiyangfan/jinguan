package com.longzhong.jinguan.ingest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 用量明细持久化端口；结算投影用条件更新保证终态不降级。 */
public interface UsageRecordRepository extends JpaRepository<UsageRecordEntity, Long> {

  String PARAM_USAGE_ID = "usageId";
  String PARAM_NOW = "now";

  Optional<UsageRecordEntity> findByUsageId(String usageId);

  Page<UsageRecordEntity> findBySubjectOrderByIdDesc(String subject, Pageable pageable);

  /** 增加尝试次数并更新投影；归属仅首次写入，已结算行不降级。 */
  @Modifying
  @Query(
      """
      UPDATE UsageRecordEntity r SET
        r.attempts = r.attempts + 1,
        r.lastAttemptAt = :now,
        r.subject = COALESCE(r.subject, :pinned),
        r.finalOutcome = CASE WHEN r.finalOutcome IN ('accepted','replayed')
                              THEN r.finalOutcome ELSE :outcome END
      WHERE r.usageId = :usageId
      """)
  int bumpAttemptAndTransition(
      @Param(PARAM_USAGE_ID) String usageId,
      @Param("outcome") String outcome,
      @Param("pinned") String pinned,
      @Param(PARAM_NOW) Instant now);

  /** 更新结算结果和轮转时间；已结算终态不降级，也不增加接收尝试次数。 */
  @Modifying
  @Query(
      """
      UPDATE UsageRecordEntity r SET
        r.lastSettlementAttemptAt = :now,
        r.finalOutcome = CASE WHEN r.finalOutcome IN ('accepted','replayed')
                              THEN r.finalOutcome ELSE :outcome END
      WHERE r.usageId = :usageId
      """)
  int applySettlementOutcome(
      @Param(PARAM_USAGE_ID) String usageId,
      @Param("outcome") String outcome,
      @Param(PARAM_NOW) Instant now);

  /** 仅刷新结算尝试时间。 */
  @Modifying
  @Query(
      "UPDATE UsageRecordEntity r SET r.lastSettlementAttemptAt = :now"
          + " WHERE r.usageId = :usageId")
  int touchSettlementAttempt(@Param(PARAM_USAGE_ID) String usageId, @Param(PARAM_NOW) Instant now);

  /** 增加恢复尝试次数并更新时间。 */
  @Modifying
  @Query(
      "UPDATE UsageRecordEntity r SET r.attempts = r.attempts + 1, r.lastAttemptAt = :now"
          + " WHERE r.usageId = :usageId")
  int bumpRecoveryAttempt(@Param(PARAM_USAGE_ID) String usageId, @Param(PARAM_NOW) Instant now);

  /** 按最近结算尝试时间轮转候选用户；从未尝试者优先，同刻按用户 ID 排序。 */
  @Query(
      value =
          """
          SELECT c.subject FROM (
            SELECT DISTINCT subject FROM usage_records
            WHERE final_outcome IN ('pending','skipped_insufficient') AND subject IS NOT NULL
          ) c
          ORDER BY (SELECT MAX(r.last_settlement_attempt_at) FROM usage_records r
                    WHERE r.subject = c.subject) ASC NULLS FIRST,
                   c.subject ASC
          LIMIT :maxUsers
          """,
      nativeQuery = true)
  List<String> findCandidateUsers(@Param("maxUsers") int maxUsers);

  /** 查询单用户的待结算行，按首次接收顺序排列并限制数量。 */
  @Query(
      value =
          """
          SELECT id, usage_id, key_id, tenant_id, model_alias, upstream_model, status,
                 error_class, prompt_tokens, completion_tokens, reasoning_tokens,
                 cached_tokens, cost_credits, currency, source_created_at, subject,
                 final_outcome, attempts, last_attempt_at, last_settlement_attempt_at
          FROM usage_records
          WHERE subject = :subject AND final_outcome IN ('pending','skipped_insufficient')
          ORDER BY id ASC
          LIMIT :maxRows
          """,
      nativeQuery = true)
  List<UsageRecordEntity> findCandidateRows(
      @Param("subject") String subject, @Param("maxRows") int maxRows);
}
