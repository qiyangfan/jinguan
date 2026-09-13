package com.longzhong.jinguan.ingest;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 用量明细留存:每行独立小事务落库(幂等:usage_id 已存在则更新投影与 attempts, 不新建行)——明细与资金解耦,不阻断扣账。 并发同 usage_id 投递(muniu
 * 并发重投):唯一约束败方 → 新事务重读走投影更新,不新建行、不以 500 中断整批; 投影(final_outcome/attempts)非资金事实,资金真相在
 * ledger_entries(经 refId="usage:{ID}" 关联), 账本 append-only 纪律不约束本表。
 */
@Service
public class UsageRecordService {

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
   * 单行明细落库/投影更新。调用契约:仅限【无外部事务】的调用方(当前唯一调用方 IngestService.accept 逐行独立事务)——DIVE
   * 重试依赖失败事务已回滚,参与外部事务时不可就地重试。
   */
  public void record(IngestUsageRow row, String outcome, Long userId) {
    if (org.springframework.transaction.support.TransactionSynchronizationManager
        .isActualTransactionActive()) {
      throw new IllegalStateException("UsageRecordService.record 不得在外部事务内调用");
    }
    try {
      tx.execute(s -> upsert(row, outcome, userId));
    } catch (DataIntegrityViolationException e) {
      // 并发同 usage_id:唯一约束败方的事务已 rollback-only → 新事务重读走投影更新
      tx.execute(s -> upsert(row, outcome, userId));
    }
  }

  /** 本人用量明细(新→旧,分页)。 */
  @Transactional(readOnly = true)
  public Page<UsageRecordEntity> byUser(long userId, int page, int size) {
    return records.findByUserIdOrderByIdDesc(userId, PageRequest.of(page, Math.min(size, 200)));
  }

  private Object upsert(IngestUsageRow row, String outcome, Long userId) {
    var existing = records.findByUsageId(row.ID()).orElse(null);
    if (existing == null) {
      records.save(UsageRecordEntity.of(row, userId, outcome));
    } else {
      existing.recordAttempt(outcome, userId);
      records.save(existing);
    }
    return null;
  }
}
