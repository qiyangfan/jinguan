package com.longzhong.jinguan.wallet;

import com.longzhong.jinguan.common.CommittedMetrics;
import com.longzhong.jinguan.common.IdempotencyConflictException;
import com.longzhong.jinguan.common.InsufficientFundsException;
import com.longzhong.jinguan.common.MetricNames;
import com.longzhong.jinguan.user.SuspensionService;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 钱包账本的唯一写入口：只追加分录，以 refId 保证幂等，并在按用户串行的事务中维护非负余额。 */
@Service
public class WalletService {

  public static final String REASON_MANUAL_TOPUP = "MANUAL_TOPUP";
  public static final String REASON_PAYMENT_TOPUP = "PAYMENT_TOPUP";
  public static final String REASON_USAGE = "USAGE";
  public static final String REASON_MANUAL_ADJUST = "MANUAL_ADJUST";

  private final LedgerEntryRepository ledger;
  private final TransactionTemplate tx;
  private final SuspensionService suspension;
  private final CommittedMetrics metrics;
  private final UserLockRegistry locks;
  private final LedgerEntryWriter writer;

  public WalletService(
      LedgerEntryRepository ledger,
      org.springframework.transaction.PlatformTransactionManager tm,
      SuspensionService suspension,
      CommittedMetrics metrics,
      UserLockRegistry locks,
      LedgerEntryWriter writer) {
    this.ledger = ledger;
    this.tx = new TransactionTemplate(tm);
    this.suspension = suspension;
    this.metrics = metrics;
    this.locks = locks;
    this.writer = writer;
  }

  /** 账本写入结果；replayed 表示返回了既有分录。 */
  public record EntryResult(LedgerEntryEntity entry, boolean replayed) {}

  /** 入账；amount 必须为正数，管理员操作时记录操作人。 */
  @SuppressWarnings("PMD.UseObjectForClearerAPI") // 资金操作参数固定且短小,容器对象无收益
  public EntryResult credit(
      String subject,
      long amountCredits,
      String reason,
      String refId,
      String note,
      String operatorSubject) {
    long amount = requirePositive(amountCredits);
    return counted(
        MetricNames.KIND_CREDIT,
        () -> apply(subject, amount, reason, refId, note, operatorSubject));
  }

  /** 扣账；先按 refId 裁决重放或冲突，再检查余额。余额不足时不写账，并在锁内标记停服。 */
  @SuppressWarnings("PMD.UseObjectForClearerAPI")
  public EntryResult debit(
      String subject,
      long amountCredits,
      String reason,
      String refId,
      String note,
      String operatorSubject) {
    return counted(
        MetricNames.KIND_DEBIT,
        () ->
            locks.run(
                subject,
                () -> debitLocked(subject, amountCredits, reason, refId, note, operatorSubject)));
  }

  private EntryResult debitLocked(
      String subject,
      long amountCredits,
      String reason,
      String refId,
      String note,
      String operatorSubject) {
    long signed = -requirePositive(amountCredits);
    boolean outerTxActive = TransactionSynchronizationManager.isActualTransactionActive();
    try {
      return tx.execute(
          status -> {
            var existing = ledger.findByRefId(refId);
            if (existing.isPresent()) {
              return writer.adjudicate(existing.get(), subject, signed, reason);
            }
            return insertIfAffordable(
                subject, signed, amountCredits, reason, refId, note, operatorSubject);
          });
    } catch (InsufficientFundsException e) {
      suspension.markSuspended(subject); // 扣账回滚后，在同一用户锁内单独标记停服。
      throw e;
    } catch (DataIntegrityViolationException e) {
      if (outerTxActive) {
        throw e; // 外层事务已标记回滚，交由渠道重试时重放。
      }
      // 独立事务下重读唯一约束胜方，裁决为重放或载荷冲突。
      return tx.execute(
          status -> {
            var existing = ledger.findByRefId(refId);
            if (existing.isEmpty()) {
              throw e; // 重读无分录，说明完整性错误并非唯一键竞争。
            }
            return writer.adjudicate(existing.get(), subject, signed, reason);
          });
    }
  }

  /** 余额不足以覆盖扣账时抛出停服异常；throw 置于独立方法，供调用方统一标记停服。 */
  private EntryResult insertIfAffordable(
      String subject,
      long signed,
      long requested,
      String reason,
      String refId,
      String note,
      String operatorSubject) {
    long current = balance(subject);
    if (current + signed < 0) {
      throw new InsufficientFundsException(subject, requested, current);
    }
    return new EntryResult(
        ledger.save(LedgerEntryEntity.of(subject, signed, reason, refId, note, operatorSubject)),
        false);
  }

  @Transactional(readOnly = true)
  public long balance(String subject) {
    return ledger.sumBySubject(subject);
  }

  @Transactional(readOnly = true)
  public Page<LedgerEntryEntity> history(String subject, int page, int size) {
    return ledger.findBySubjectOrderByIdDesc(subject, PageRequest.of(page, Math.min(size, 200)));
  }

  private EntryResult apply(
      String subject,
      long amount,
      String reason,
      String refId,
      String note,
      String operatorSubject) {
    return locks.run(
        subject,
        () -> {
          boolean outerTxActive = TransactionSynchronizationManager.isActualTransactionActive();
          try {
            return tx.execute(
                status -> {
                  EntryResult result =
                      writer.insertOrReplay(subject, amount, reason, refId, note, operatorSubject);
                  // 与入账同事务清除停服，避免充值成功后账号仍被停用。
                  suspension.clearIfSuspended(subject);
                  return result;
                });
          } catch (DataIntegrityViolationException e) {
            if (outerTxActive) {
              throw e; // 外层事务已标记回滚，交由渠道重试时重放。
            }
            // 重读唯一约束胜方；重放不再清除停服状态。
            return tx.execute(
                status ->
                    writer.insertOrReplay(subject, amount, reason, refId, note, operatorSubject));
          }
        });
  }

  /** 分录成功在提交后计数；余额不足和幂等冲突作为拒绝结果立即计数。 */
  private EntryResult counted(String kind, Supplier<EntryResult> operation) {
    try {
      EntryResult result = operation.get();
      metrics.countOnCommit(
          MetricNames.WALLET_OPERATIONS,
          MetricNames.TAG_KIND,
          kind,
          MetricNames.TAG_OUTCOME,
          result.replayed() ? MetricNames.OUTCOME_REPLAYED : MetricNames.OUTCOME_APPLIED);
      return result;
    } catch (InsufficientFundsException e) {
      metrics.count(
          MetricNames.WALLET_OPERATIONS,
          MetricNames.TAG_KIND,
          kind,
          MetricNames.TAG_OUTCOME,
          MetricNames.OUTCOME_REJECTED_INSUFFICIENT);
      throw e;
    } catch (IdempotencyConflictException e) {
      metrics.count(
          MetricNames.WALLET_OPERATIONS,
          MetricNames.TAG_KIND,
          kind,
          MetricNames.TAG_OUTCOME,
          MetricNames.OUTCOME_REJECTED_CONFLICT);
      throw e;
    }
  }

  private static long requirePositive(long amountCredits) {
    if (amountCredits <= 0) {
      throw new IllegalArgumentException("金额必须为正(credits): " + amountCredits);
    }
    return amountCredits;
  }
}
