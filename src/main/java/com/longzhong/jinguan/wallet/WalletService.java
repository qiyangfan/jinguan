package com.longzhong.jinguan.wallet;

import com.longzhong.jinguan.common.CommittedMetrics;
import com.longzhong.jinguan.common.IdempotencyConflictException;
import com.longzhong.jinguan.common.InsufficientFundsException;
import com.longzhong.jinguan.common.MetricNames;
import com.longzhong.jinguan.user.SuspensionService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
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
  private final Map<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

  public WalletService(
      LedgerEntryRepository ledger,
      org.springframework.transaction.PlatformTransactionManager tm,
      SuspensionService suspension,
      CommittedMetrics metrics) {
    this.ledger = ledger;
    this.tx = new TransactionTemplate(tm);
    this.suspension = suspension;
    this.metrics = metrics;
  }

  /** 账本写入结果；replayed 表示返回了既有分录。 */
  public record EntryResult(LedgerEntryEntity entry, boolean replayed) {}

  /** 入账；amount 必须为正数，管理员操作时记录操作人。 */
  public EntryResult credit(
      long userId,
      long amountCredits,
      String reason,
      String refId,
      String note,
      Long operatorUserId) {
    long amount = requirePositive(amountCredits);
    return counted(
        MetricNames.KIND_CREDIT, () -> apply(userId, amount, reason, refId, note, operatorUserId));
  }

  /** 扣账；先按 refId 裁决重放或冲突，再检查余额。余额不足时不写账，并在锁内标记停服。 */
  public EntryResult debit(
      long userId,
      long amountCredits,
      String reason,
      String refId,
      String note,
      Long operatorUserId) {
    return counted(
        MetricNames.KIND_DEBIT,
        () ->
            withUserLock(
                userId,
                () -> debitLocked(userId, amountCredits, reason, refId, note, operatorUserId)));
  }

  private EntryResult debitLocked(
      long userId,
      long amountCredits,
      String reason,
      String refId,
      String note,
      Long operatorUserId) {
    long signed = -requirePositive(amountCredits);
    boolean outerTxActive = TransactionSynchronizationManager.isActualTransactionActive();
    try {
      return tx.execute(
          status -> {
            var existing = ledger.findByRefId(refId);
            if (existing.isPresent()) {
              return adjudicate(existing.get(), userId, signed, reason);
            }
            long current = balance(userId);
            if (current + signed < 0) {
              throw new InsufficientFundsException(userId, amountCredits, current);
            }
            return new EntryResult(
                ledger.save(
                    LedgerEntryEntity.of(userId, signed, reason, refId, note, operatorUserId)),
                false);
          });
    } catch (InsufficientFundsException e) {
      suspension.markSuspended(userId); // 扣账回滚后，在同一用户锁内单独标记停服。
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
            return adjudicate(existing.get(), userId, signed, reason);
          });
    }
  }

  @Transactional(readOnly = true)
  public long balance(long userId) {
    return ledger.sumByUserId(userId);
  }

  @Transactional(readOnly = true)
  public Page<LedgerEntryEntity> history(long userId, int page, int size) {
    return ledger.findByUserIdOrderByIdDesc(userId, PageRequest.of(page, Math.min(size, 200)));
  }

  private EntryResult apply(
      long userId, long amount, String reason, String refId, String note, Long operatorUserId) {
    return withUserLock(
        userId,
        () -> {
          boolean outerTxActive = TransactionSynchronizationManager.isActualTransactionActive();
          try {
            return tx.execute(
                status -> {
                  EntryResult result =
                      insertOrReplay(userId, amount, reason, refId, note, operatorUserId);
                  // 与入账同事务清除停服，避免充值成功后账号仍被停用。
                  suspension.clearIfSuspended(userId);
                  return result;
                });
          } catch (DataIntegrityViolationException e) {
            if (outerTxActive) {
              throw e; // 外层事务已标记回滚，交由渠道重试时重放。
            }
            // 重读唯一约束胜方；重放不再清除停服状态。
            return tx.execute(
                status -> insertOrReplay(userId, amount, reason, refId, note, operatorUserId));
          }
        });
  }

  /** 按用户串行执行资金操作，并在外层事务提交或回滚后释放锁。 */
  private <T> T withUserLock(long userId, Supplier<T> work) {
    ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
    lock.lock();
    boolean deferred = false;
    try {
      if (TransactionSynchronizationManager.isActualTransactionActive()
          && TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
              @Override
              public void afterCompletion(int status) {
                lock.unlock();
              }
            });
        deferred = true;
      }
      return work.get();
    } finally {
      if (!deferred) {
        lock.unlock();
      }
    }
  }

  /** 按 refId 插入分录，已存在时校验载荷并返回重放或冲突。 */
  private EntryResult insertOrReplay(
      long userId, long amount, String reason, String refId, String note, Long operatorUserId) {
    var existing = ledger.findByRefId(refId);
    if (existing.isPresent()) {
      return adjudicate(existing.get(), userId, amount, reason);
    }
    return new EntryResult(
        ledger.save(LedgerEntryEntity.of(userId, amount, reason, refId, note, operatorUserId)),
        false);
  }

  /** 同 refId 的用户、金额或原因不一致时抛出幂等冲突。 */
  private EntryResult adjudicate(
      LedgerEntryEntity existing, long userId, long amount, String reason) {
    if (existing.getUserId() != userId
        || existing.getAmountCredits() != amount
        || !existing.getReason().equals(reason)) {
      throw new IdempotencyConflictException(existing.getRefId());
    }
    return new EntryResult(existing, true);
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
