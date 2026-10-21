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

/** 保存用量事实并更新结算结果投影；资金事实以账本分录为准。 */
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

  /** 保存或校验用量行；不得在外部事务内调用，唯一键冲突后在新事务中重读。 */
  public Long reserve(IngestUsageRow row, Long boundUser, boolean billable) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("UsageRecordService.reserve 不得在外部事务内调用");
    }
    try {
      return tx.execute(s -> reserveRow(row, boundUser, billable));
    } catch (DataIntegrityViolationException e) {
      // 并发插入同一 usage_id 时，在新事务中重读并更新已有行。
      return tx.execute(s -> reserveRow(row, boundUser, billable));
    }
  }

  /** 更新结算结果和尝试时间；不降低已结算终态，也不重复增加尝试次数。 */
  public void applySettlementOutcome(String usageId, String outcome) {
    Instant now = Instant.now();
    tx.executeWithoutResult(s -> records.applySettlementOutcome(usageId, outcome, now));
  }

  /** 记录一次恢复尝试，不改变结算结果。 */
  public void bumpRecoveryAttempt(String usageId) {
    Instant now = Instant.now();
    tx.executeWithoutResult(s -> records.bumpRecoveryAttempt(usageId, now));
  }

  /** 仅刷新结算尝试时间。 */
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
      // 相同 ID 的计费事实不一致时拒绝写账，不覆盖原记录。
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

  /** 查询用户用量明细，按新到旧分页。 */
  @Transactional(readOnly = true)
  public Page<UsageRecordEntity> byUser(long userId, int page, int size) {
    return records.findByUserIdOrderByIdDesc(userId, PageRequest.of(page, Math.min(size, 200)));
  }
}
