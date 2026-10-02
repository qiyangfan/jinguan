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

public interface UsageRecordRepository extends JpaRepository<UsageRecordEntity, Long> {

  Optional<UsageRecordEntity> findByUsageId(String usageId);

  Page<UsageRecordEntity> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

  /**
   * 接收/恢复尝试计数(attempts 单调 +1)与本轮过渡投影。 归属只允许「首次合法解析」落位:已有归属不可改写(key 重绑不转嫁)。
   * 已结算终态(accepted/replayed)不降级——条件写在 SQL 内,天然防并发结果倒退。 时间由调用方传 Instant(Hibernate 6 的
   * CURRENT_TIMESTAMP 推断为 Timestamp,与 Instant 字段不兼容)。
   */
  @Modifying
  @Query(
      """
      UPDATE UsageRecordEntity r SET
        r.attempts = r.attempts + 1,
        r.lastAttemptAt = :now,
        r.userId = COALESCE(r.userId, :pinned),
        r.finalOutcome = CASE WHEN r.finalOutcome IN ('accepted','replayed')
                              THEN r.finalOutcome ELSE :outcome END
      WHERE r.usageId = :usageId
      """)
  int bumpAttemptAndTransition(
      @Param("usageId") String usageId,
      @Param("outcome") String outcome,
      @Param("pinned") Long pinned,
      @Param("now") Instant now);

  /** 结算尝试结果落投影:总是刷新内部结算尝试时间(用户轮转据此推进);终态不降级。 attempts 不在此递增——预留/恢复尝试步进已计,投影更新不双计。 */
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
      @Param("usageId") String usageId,
      @Param("outcome") String outcome,
      @Param("now") Instant now);

  /** 只刷新内部结算尝试时间(实际发生过结算尝试但结论不变时,如幂等冲突行)。 */
  @Modifying
  @Query(
      "UPDATE UsageRecordEntity r SET r.lastSettlementAttemptAt = :now"
          + " WHERE r.usageId = :usageId")
  int touchSettlementAttempt(@Param("usageId") String usageId, @Param("now") Instant now);

  /** 恢复尝试步进:attempts 单调 +1、last_attempt_at 刷新;投影结论由结算结果另行落位。 */
  @Modifying
  @Query(
      "UPDATE UsageRecordEntity r SET r.attempts = r.attempts + 1, r.lastAttemptAt = :now"
          + " WHERE r.usageId = :usageId")
  int bumpRecoveryAttempt(@Param("usageId") String usageId, @Param("now") Instant now);

  /**
   * 候选用户(有界,公平轮转):存在 pending/欠费候选行的用户,按「全部固定归属明细的最大结算尝试时间」升序—— 从未实际尝试的用户优先(NULLS
   * FIRST),其后按最近尝试时间升序轮转,同刻以稳定用户 ID 决胜; 退出候选的成功行其尝试历史仍参与排序,不重新获得首次优先级。
   */
  @Query(
      value =
          """
          SELECT c.user_id FROM (
            SELECT DISTINCT user_id FROM usage_records
            WHERE final_outcome IN ('pending','skipped_insufficient') AND user_id IS NOT NULL
          ) c
          ORDER BY (SELECT MAX(r.last_settlement_attempt_at) FROM usage_records r
                    WHERE r.user_id = c.user_id) ASC NULLS FIRST,
                   c.user_id ASC
          LIMIT :maxUsers
          """,
      nativeQuery = true)
  List<Long> findCandidateUsers(@Param("maxUsers") int maxUsers);

  /** 单用户候选行(有界):按首次接收顺序(id 升序)处理,余额不足即止于该行。列名化(禁 SELECT *)。 */
  @Query(
      value =
          """
          SELECT id, usage_id, key_id, tenant_id, model_alias, upstream_model, status,
                 error_class, prompt_tokens, completion_tokens, reasoning_tokens,
                 cached_tokens, cost_credits, currency, source_created_at, user_id,
                 final_outcome, attempts, last_attempt_at, last_settlement_attempt_at
          FROM usage_records
          WHERE user_id = :userId AND final_outcome IN ('pending','skipped_insufficient')
          ORDER BY id ASC
          LIMIT :maxRows
          """,
      nativeQuery = true)
  List<UsageRecordEntity> findCandidateRows(
      @Param("userId") long userId, @Param("maxRows") int maxRows);
}
