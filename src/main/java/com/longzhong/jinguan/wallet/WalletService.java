package com.longzhong.jinguan.wallet;

import com.longzhong.jinguan.common.IdempotencyConflictException;
import com.longzhong.jinguan.common.InsufficientFundsException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 钱包账本服务——本服务唯一的领域核心,全部资金不变量内聚于此:
 *
 * <ul>
 *   <li>append-only:只提供 credit/debit,不暴露任何修改/删除通道;
 *   <li>幂等:refId 唯一约束兜底,重放同键静默返回既有结果;同键不同载荷(用户/金额)抛冲突;
 *   <li>非负不变量:扣账后余额不得为负,违反即拒绝且不留痕;
 *   <li>并发:per-user 条带锁内含【完整编程式事务】(TransactionTemplate)——锁的释放 晚于事务提交,杜绝「锁外提交窗口读到旧余额」的超扣竞态;写入路径一律
 *       REQUIRED 传播,支付入账事件参与调用方事务时语义不变。 集群化需换账本行锁或分布式锁,见 README。
 * </ul>
 */
@Service
public class WalletService {

  public static final String REASON_MANUAL_TOPUP = "MANUAL_TOPUP";
  public static final String REASON_PAYMENT_TOPUP = "PAYMENT_TOPUP";
  public static final String REASON_USAGE = "USAGE";

  private final LedgerEntryRepository ledger;
  private final TransactionTemplate tx;
  private final Map<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

  public WalletService(
      LedgerEntryRepository ledger, org.springframework.transaction.PlatformTransactionManager tm) {
    this.ledger = ledger;
    this.tx = new TransactionTemplate(tm);
  }

  /** 账本写入结果:entry=生效分录(新写或既有),replayed=幂等重放(非新写)。 */
  public record EntryResult(LedgerEntryEntity entry, boolean replayed) {}

  /** 入账(手工兜底/支付回调/其他来源)。amount 必须 > 0。 */
  public EntryResult credit(
      long userId, long amountMicros, String reason, String refId, String note) {
    return apply(userId, requirePositive(amountMicros), reason, refId, note);
  }

  /** 扣账(计量消费)。余额非负不变量在此裁决;不足抛 InsufficientFunds,账本零痕迹。 */
  public EntryResult debit(
      long userId, long amountMicros, String reason, String refId, String note) {
    ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
    lock.lock();
    try {
      long signed = -requirePositive(amountMicros);
      return tx.execute(
          status -> {
            if (balance(userId) + signed < 0) {
              throw new InsufficientFundsException(userId, amountMicros, balance(userId));
            }
            return insertOrReplay(userId, signed, reason, refId, note);
          });
    } finally {
      lock.unlock();
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

  private EntryResult apply(long userId, long amount, String reason, String refId, String note) {
    ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
    lock.lock();
    try {
      return tx.execute(status -> insertOrReplay(userId, amount, reason, refId, note));
    } finally {
      lock.unlock();
    }
  }

  /** 幂等写入:refId 已存在——关键字段一致则重放(返回既有分录),不一致则冲突。 */
  private EntryResult insertOrReplay(
      long userId, long amount, String reason, String refId, String note) {
    var existing = ledger.findByRefId(refId);
    if (existing.isPresent()) {
      LedgerEntryEntity e = existing.get();
      if (e.getUserId() != userId
          || e.getAmountMicros() != amount
          || !e.getReason().equals(reason)) {
        throw new IdempotencyConflictException(refId);
      }
      return new EntryResult(e, true); // 同键同载荷:重放,返回既有结果(不再插入)
    }
    return new EntryResult(
        ledger.save(LedgerEntryEntity.of(userId, amount, reason, refId, note)), false);
  }

  private static long requirePositive(long amountMicros) {
    if (amountMicros <= 0) {
      throw new IllegalArgumentException("金额必须为正(micros): " + amountMicros);
    }
    return amountMicros;
  }
}
