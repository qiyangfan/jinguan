package com.longzhong.jinguan.wallet;

import com.longzhong.jinguan.common.IdempotencyConflictException;
import com.longzhong.jinguan.common.InsufficientFundsException;
import com.longzhong.jinguan.user.SuspensionService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 钱包账本服务——本服务唯一的领域核心,全部资金不变量内聚于此:
 *
 * <ul>
 *   <li>append-only:只提供 credit/debit,不暴露任何修改/删除通道;
 *   <li>幂等:refId 唯一约束兜底,重放同键静默返回既有结果;同键不同载荷(用户/金额)抛冲突;
 *   <li>非负不变量:扣账后余额不得为负,违反即拒绝且不留痕;
 *   <li>并发:per-user 条带锁内含【完整编程式事务】(TransactionTemplate),独立事务路径下锁的释放晚于提交, 杜绝「锁外提交窗口读到旧余额」的超扣竞态;
 *       支付入账经 BEFORE_COMMIT 参与外部事务时,锁覆盖自身事务、提交随外部事务——并发扣账读不到 未提交入账只会更保守(少看入账方向),非负不变量不受影响。
 *       唯一约束败方的就地重放仅在【无外部事务】时进行(参与外部事务时该事务已 rollback-only,
 *       就地重放不可恢复,整单回滚交由渠道重试走预检重放)。集群化需换账本行锁或分布式锁,见 README。
 * </ul>
 */
@Service
public class WalletService {

  public static final String REASON_MANUAL_TOPUP = "MANUAL_TOPUP";
  public static final String REASON_PAYMENT_TOPUP = "PAYMENT_TOPUP";
  public static final String REASON_USAGE = "USAGE";
  public static final String REASON_MANUAL_ADJUST = "MANUAL_ADJUST";

  private final LedgerEntryRepository ledger;
  private final TransactionTemplate tx;
  private final SuspensionService suspension;
  private final Map<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

  public WalletService(
      LedgerEntryRepository ledger,
      org.springframework.transaction.PlatformTransactionManager tm,
      SuspensionService suspension) {
    this.ledger = ledger;
    this.tx = new TransactionTemplate(tm);
    this.suspension = suspension;
  }

  /** 账本写入结果:entry=生效分录(新写或既有),replayed=幂等重放(非新写)。 */
  public record EntryResult(LedgerEntryEntity entry, boolean replayed) {}

  /** 入账(手工兜底/支付回调/其他来源)。amount 必须 > 0;operator 为管理员发起时的操作人(审计)。 */
  public EntryResult credit(
      long userId,
      long amountCredits,
      String reason,
      String refId,
      String note,
      Long operatorUserId) {
    return apply(userId, requirePositive(amountCredits), reason, refId, note, operatorUserId);
  }

  /**
   * 扣账(计量消费/管理员调账)。余额非负不变量在此裁决;不足抛 InsufficientFunds,账本零痕迹。 欠费停服置位在本方法锁内、独立事务执行(InsufficientFunds
   * 路径):与并发入账(apply 锁内清除) 严格串行——置位落在入账之后时,余额已充足,条件 UPDATE 仍会置位造成的「有钱被停服」由入账兜底不可达;
   * 反序(入账后置位)亦被锁序排除。置位于 debit 事务回滚后执行,自身失败不改变资金语义。
   */
  public EntryResult debit(
      long userId,
      long amountCredits,
      String reason,
      String refId,
      String note,
      Long operatorUserId) {
    ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
    lock.lock();
    try {
      long signed = -requirePositive(amountCredits);
      boolean outerTxActive = TransactionSynchronizationManager.isActualTransactionActive();
      try {
        return tx.execute(
            status -> {
              if (balance(userId) + signed < 0) {
                throw new InsufficientFundsException(userId, amountCredits, balance(userId));
              }
              return insertOrReplay(userId, signed, reason, refId, note, operatorUserId);
            });
      } catch (InsufficientFundsException e) {
        suspension.markSuspended(userId); // 锁内、独立小事务(彼时 debit 事务已回滚);幂等
        throw e;
      } catch (DataIntegrityViolationException e) {
        if (outerTxActive) {
          throw e; // 参与外部事务:本事务已 rollback-only,就地重放不可恢复——整单回滚后由渠道重试走预检重放
        }
        // 独立事务:唯一约束败方 → 新事务重读既有分录,按幂等重放/冲突裁决(禁止以裸 500 替代重放)
        return tx.execute(
            status -> insertOrReplay(userId, signed, reason, refId, note, operatorUserId));
      }
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

  private EntryResult apply(
      long userId, long amount, String reason, String refId, String note, Long operatorUserId) {
    ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
    lock.lock();
    try {
      boolean outerTxActive = TransactionSynchronizationManager.isActualTransactionActive();
      try {
        return tx.execute(
            status -> {
              EntryResult result =
                  insertOrReplay(userId, amount, reason, refId, note, operatorUserId);
              // 充值即恢复:与入账分录同事务清除欠费停服(幂等;恢复 choke point 一切入账必经)——
              // 不可能出现「入账成功但停服未解除」; SuspensionService 内部仅在状态实际变化时 bump 水位
              suspension.clearIfSuspended(userId);
              return result;
            });
      } catch (DataIntegrityViolationException e) {
        if (outerTxActive) {
          throw e; // 参与外部事务(支付 BEFORE_COMMIT 链路):rollback-only,就地重放不可恢复——整单回滚后由渠道重试收敛
        }
        // 独立事务:唯一约束败方 → 新事务重读既有分录,按幂等重放/冲突裁决(禁止以裸 500 替代重放);
        // 重放路径不重复 clear(原成功入账已同事务清除,重放无新增资金)
        return tx.execute(
            status -> insertOrReplay(userId, amount, reason, refId, note, operatorUserId));
      }
    } finally {
      lock.unlock();
    }
  }

  /** 幂等写入:refId 已存在——关键字段一致则重放(返回既有分录),不一致则冲突。 */
  private EntryResult insertOrReplay(
      long userId, long amount, String reason, String refId, String note, Long operatorUserId) {
    var existing = ledger.findByRefId(refId);
    if (existing.isPresent()) {
      LedgerEntryEntity e = existing.get();
      if (e.getUserId() != userId
          || e.getAmountCredits() != amount
          || !e.getReason().equals(reason)) {
        throw new IdempotencyConflictException(refId);
      }
      return new EntryResult(e, true); // 同键同载荷:重放,返回既有结果(不再插入)
    }
    return new EntryResult(
        ledger.save(LedgerEntryEntity.of(userId, amount, reason, refId, note, operatorUserId)),
        false);
  }

  private static long requirePositive(long amountCredits) {
    if (amountCredits <= 0) {
      throw new IllegalArgumentException("金额必须为正(credits): " + amountCredits);
    }
    return amountCredits;
  }
}
