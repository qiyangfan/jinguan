package com.longzhong.jinguan.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.longzhong.jinguan.common.InsufficientFundsException;
import com.longzhong.jinguan.user.UserEntity;
import com.longzhong.jinguan.user.UserRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 资金互斥覆盖物理事务完成(wallet-ledger delta ADDED):支付入账参与外层事务时,
 * 同用户锁须持有到外层物理提交/回滚完成——恢复器不得读到充值前余额而在充值提交后错误重新停服; 外层回滚后互斥正常释放。
 */
@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-outertx;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
    })
class WalletOuterTransactionLockTest {

  private static final long CREDIT = 100_000;
  private static final long DEBIT = 60_000;

  @Autowired WalletService wallet;
  @Autowired UserRepository users;
  @Autowired PlatformTransactionManager tm;

  private long newUser(String username) {
    return users.save(UserEntity.create(username, "hash", "USER")).getId();
  }

  @Test
  void outerCommit_concurrentRecovery_seesCreditedBalance() throws Exception {
    long uid = newUser("outertx-u1");
    TransactionTemplate outer = new TransactionTemplate(tm);
    CountDownLatch creditReturned = new CountDownLatch(1);
    CountDownLatch recoveryStarted = new CountDownLatch(1);
    CountDownLatch recoveryDone = new CountDownLatch(1);
    AtomicReference<Throwable> recoveryError = new AtomicReference<>();

    Thread recovery =
        new Thread(
            () -> {
              try {
                creditReturned.await(5, TimeUnit.SECONDS);
                recoveryStarted.countDown();
                wallet.debit(
                    uid, DEBIT, WalletService.REASON_USAGE, "usage:outertx-r1", null, null);
              } catch (Throwable t) {
                recoveryError.set(t);
              } finally {
                recoveryDone.countDown();
              }
            },
            "recovery-probe");
    recovery.start();

    outer.execute(
        status -> {
          // 模拟 Mock 支付入账:BEFORE_COMMIT 监听链路等价于「credit 参与本外层事务」
          wallet.credit(
              uid, CREDIT, WalletService.REASON_PAYMENT_TOPUP, "pay:outertx-1", null, null);
          creditReturned.countDown(); // credit 已返回、外层尚未物理提交
          try {
            assertTrue(recoveryStarted.await(5, TimeUnit.SECONDS), "恢复线程应已发起");
            // 旧实现(方法返回即解锁):恢复线程此刻已读到充值前余额 0 → 欠费停服;
            // 修复后:恢复线程阻塞在锁上,等待窗口留痕后照常提交
            recoveryDone.await(1, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return null; // 此处返回才触发物理提交
        });
    assertTrue(recoveryDone.await(10, TimeUnit.SECONDS), "恢复线程应在提交后完成");
    recovery.join(10_000);

    assertNull(recoveryError.get(), "恢复扣款应在提交后的余额上判断,不得因读到充值前余额而失败");
    assertFalse(users.findSuspendedIds().contains(uid), "不得在充值提交后错误重新停服");
    assertEquals(CREDIT - DEBIT, wallet.balance(uid), "分录与余额一致");
    assertTrue(wallet.balance(uid) >= 0, "非负不变量保持");
  }

  @Test
  void outerRollback_lockReleased_nothingPersisted() {
    long uid = newUser("outertx-u2");
    TransactionTemplate outer = new TransactionTemplate(tm);

    outer.execute(
        status -> {
          wallet.credit(
              uid, CREDIT, WalletService.REASON_PAYMENT_TOPUP, "pay:outertx-rb", null, null);
          status.setRollbackOnly(); // 外层回滚:未提交的入账与停服清除均不得生效
          return null;
        });

    assertEquals(0, wallet.balance(uid), "回滚后入账不生效");
    // 互斥正常释放的实证:同用户后续资金操作可继续(余额 0 → 扣款拒绝且锁内停服)
    assertThrows(
        InsufficientFundsException.class,
        () -> wallet.debit(uid, 1, WalletService.REASON_USAGE, "usage:outertx-rb-1", null, null));
    assertTrue(users.findSuspendedIds().contains(uid), "无外层事务时扣款拒绝仍在锁内置位停服");
    // 再入账:锁可用且停服解除(既有恢复路径不受影响)
    wallet.credit(uid, 10, WalletService.REASON_MANUAL_TOPUP, "outertx-after-rb", null, null);
    assertFalse(users.findSuspendedIds().contains(uid), "入账后停服解除");
    assertEquals(10, wallet.balance(uid));
  }
}
