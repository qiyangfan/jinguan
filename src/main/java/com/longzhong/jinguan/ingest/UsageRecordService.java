package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.common.IdempotencyConflictException;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 用量明细留存与结算投影:接收侧行先可靠预留再进资金通道(2xx = 需要处理的用量已可靠落库, 结算可以暂缓);恢复侧从已落库候选继续,经 usage:{ID} 幂等键修复投影。
 * 明细与资金解耦:投影(final_outcome/attempts)非资金事实,资金真相在 ledger_entries(经 refId 关联), 账本 append-only
 * 纪律不约束本表——但不可变计费事实与首次归属固定,同 ID 不同事实在资金写入前报幂等冲突。
 */
@Service
public class UsageRecordService {

  public static final String OUTCOME_PENDING = "pending";
  public static final String OUTCOME_ACCEPTED = "accepted";
  public static final String OUTCOME_REPLAYED = "replayed";
  public static final String OUTCOME_SKIPPED_UNBOUND = "skipped_unbound";
  public static final String OUTCOME_SKIPPED_FREE = "skipped_free";
  public static final String OUTCOME_SKIPPED_INSUFFICIENT = "skipped_insufficient";

  private final UsageRecordRepository records;
  private final TransactionTemplate tx;

  public UsageRecordService(
      UsageRecordRepository records,
      org.springframework.transaction.PlatformTransactionManager tm) {
    this.records = records;
    this.tx = new TransactionTemplate(tm);
  }

  /**
   * 可靠预留(接收侧行入口,资金处理前必经):首次落库或既有行事实核对,attempts 单调 +1。 调用契约:仅限【无外部事务】的调用方(当前唯一调用方
   * IngestService.accept 逐行独立事务)——DIVE 重试依赖失败事务已回滚。 存储失败/同 ID 不同事实向上抛(非 2xx);此前已提交行不回滚。
   *
   * @return 固定归属用户:既有行的已固定归属优先(重绑不转嫁),新行/未固定行为本次解析结果(可空)
   */
  public Long reserve(IngestUsageRow row, Long boundUser, boolean billable) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("UsageRecordService.reserve 不得在外部事务内调用");
    }
    try {
      return tx.execute(s -> reserveRow(row, boundUser, billable));
    } catch (DataIntegrityViolationException e) {
      // 并发同 usage_id:唯一约束败方的事务已 rollback-only → 新事务重读走核对/计数更新
      return tx.execute(s -> reserveRow(row, boundUser, billable));
    }
  }

  /** 结算尝试结果落投影:刷新内部结算尝试时间,终态不降级;与预留的尝试计数不双计。 */
  public void applySettlementOutcome(String usageId, String outcome) {
    Instant now = Instant.now();
    tx.executeWithoutResult(s -> records.applySettlementOutcome(usageId, outcome, now));
  }

  /** 恢复尝试步进(每次恢复尝试恰计一次 attempts),不改变投影结论。 */
  public void bumpRecoveryAttempt(String usageId) {
    Instant now = Instant.now();
    tx.executeWithoutResult(s -> records.bumpRecoveryAttempt(usageId, now));
  }

  /** 只刷新内部结算尝试时间(实际发生过结算尝试但结论不变时,如幂等冲突行)。 */
  public void touchSettlementAttempt(String usageId) {
    Instant now = Instant.now();
    tx.executeWithoutResult(s -> records.touchSettlementAttempt(usageId, now));
  }

  private Long reserveRow(IngestUsageRow row, Long boundUser, boolean billable) {
    var existing = records.findByUsageId(row.ID()).orElse(null);
    if (existing == null) {
      String outcome = initialOutcome(boundUser, billable);
      records.save(UsageRecordEntity.of(row, boundUser, outcome));
      return boundUser;
    }
    if (!existing.billingFactsEqual(row)) {
      // 同 ID 不同不可变计费事实:资金写入前拒绝,旧内容不覆盖
      throw new IdempotencyConflictException("usage:" + row.ID());
    }
    Long pinned = existing.getUserId() != null ? existing.getUserId() : boundUser;
    records.bumpAttemptAndTransition(
        row.ID(), initialOutcome(pinned, billable), pinned, Instant.now());
    return pinned;
  }

  private String initialOutcome(Long user, boolean billable) {
    if (!billable) {
      return OUTCOME_SKIPPED_FREE;
    }
    return user == null ? OUTCOME_SKIPPED_UNBOUND : OUTCOME_PENDING;
  }

  /** 本人用量明细(新→旧,分页)。 */
  @Transactional(readOnly = true)
  public Page<UsageRecordEntity> byUser(long userId, int page, int size) {
    return records.findByUserIdOrderByIdDesc(userId, PageRequest.of(page, Math.min(size, 200)));
  }
}
