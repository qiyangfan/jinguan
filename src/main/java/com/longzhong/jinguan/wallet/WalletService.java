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

/**
 * 钱包账本服务——本服务唯一的领域核心,全部资金不变量内聚于此:
 *
 * <ul>
 *   <li>append-only:只提供 credit/debit,不暴露任何修改/删除通道;
 *   <li>幂等:refId 唯一约束兜底,重放同键静默返回既有结果;同键不同载荷(用户/金额)抛冲突;
 *       既有分录的重放/冲突裁决【先于】新扣款余额判断——已扣分录在低余额下重投得重放,不误报欠费/停服;
 *   <li>非负不变量:新扣款(不存在既有分录时)后余额不得为负,违反即拒绝且不留痕;
 *   <li>并发:per-user 条带锁内含【完整编程式事务】(TransactionTemplate),锁的释放以【物理事务完成】为准——
 *       独立事务路径下事务先提交、锁后释放;参与外部事务(支付 BEFORE_COMMIT 链路)时挂 afterCompletion,
 *       持有到外层物理提交/回滚完成,恢复结算不得在充值提交前读旧余额、也不得在提交后凭旧读数重新停服。
 *       余额不足后的独立停服事务仍在锁内执行。唯一约束败方的就地重放仅在【无外部事务】时进行(参与外部事务时该事务已 rollback-only,
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
    long amount = requirePositive(amountCredits);
    return counted(
        MetricNames.KIND_CREDIT, () -> apply(userId, amount, reason, refId, note, operatorUserId));
  }

  /**
   * 扣账(计量消费/管理员调账/待结算恢复)。余额非负不变量在此裁决;不足抛 InsufficientFunds,账本零痕迹。 幂等裁决序:同 refId
   * 既有分录的重放/冲突先于新扣款余额判断——已扣分录低余额重投得重放、不置位业务停服,同键不同载荷报冲突而非伪装新欠费。
   * 欠费停服置位在本方法锁内、独立事务执行(InsufficientFunds 路径):与并发入账(apply 锁内清除) 严格串行——置位落在入账之后时,余额已充足,条件 UPDATE
   * 仍会置位造成的「有钱被停服」由入账兜底不可达; 反序(入账后置位)亦被锁序排除。置位于 debit 事务回滚后执行,自身失败不改变资金语义。
   */
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
            // 幂等裁决先于余额:既有分录(同键同载荷→重放/同键不同载荷→冲突)不看当前余额
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
      suspension.markSuspended(userId); // 锁内、独立小事务(彼时 debit 事务已回滚);幂等
      throw e;
    } catch (DataIntegrityViolationException e) {
      if (outerTxActive) {
        throw e; // 参与外部事务:本事务已 rollback-only,就地重放不可恢复——整单回滚后由渠道重试走预检重放
      }
      // 独立事务:唯一约束败方 → 新事务重读既有分录,按幂等重放/冲突裁决(禁止以裸 500 替代重放)
      return tx.execute(
          status -> {
            var existing = ledger.findByRefId(refId);
            if (existing.isEmpty()) {
              throw e; // 重读仍无分录:非唯一约束成因,原样上抛
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
        });
  }

  /**
   * per-user 互斥,锁的释放以【物理事务完成】为准: 参与外部事务(支付 BEFORE_COMMIT 链路)时挂 afterCompletion——持有到外层物理提交/回滚完成,
   * 杜绝「credit 返回即解锁 → 恢复结算读充值前余额 → 充值提交后凭旧读数重新停服」的错序; 无外部事务时保留方法级 finally(独立事务先提交后解锁的既有语义)。
   * 重入安全:同一外层事务内多次同用户资金操作各注册一次 afterCompletion,与重入持有计数一一配对。
   */
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
                lock.unlock(); // 外层物理提交/回滚完成后再放锁
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

  /** 幂等写入:refId 已存在——关键字段一致则重放(返回既有分录),不一致则冲突。 */
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

  /** 既有分录裁决:同键同用户、金额与原因 → 重放;否则幂等冲突(零新增分录)。 */
  private EntryResult adjudicate(
      LedgerEntryEntity existing, long userId, long amount, String reason) {
    if (existing.getUserId() != userId
        || existing.getAmountCredits() != amount
        || !existing.getReason().equals(reason)) {
      throw new IdempotencyConflictException(existing.getRefId());
    }
    return new EntryResult(existing, true); // 同键同载荷:重放,返回既有结果(不再插入)
  }

  /**
   * 账本事实计数(指标面唯一入口,与 {@link #insertOrReplay} 的幂等裁决同口径):
   *
   * <ul>
   *   <li>生效/重放:走 afterCommit——回调链路本方法参与外层事务,外层回滚则分录不存在,不得虚增资金事实;
   *   <li>余额不足/同键不同载荷:被拒绝本身即终态事实(账本零痕迹),立即计数,回滚不改变它。
   * </ul>
   */
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
