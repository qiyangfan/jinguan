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

/** 验证资金锁覆盖外层事务提交或回滚，避免恢复器读取未提交余额。 */
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
          // 模拟支付监听器在外层事务提交前入账。
          wallet.credit(
              uid, CREDIT, WalletService.REASON_PAYMENT_TOPUP, "pay:outertx-1", null, null);
          creditReturned.countDown(); // credit 已返回、外层尚未物理提交
          try {
            assertTrue(recoveryStarted.await(5, TimeUnit.SECONDS), "恢复线程应已发起");
            // 恢复线程必须等待外层事务结束后再读取余额。
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
    // 外层事务结束后，同用户仍可继续资金操作。
    assertThrows(
        InsufficientFundsException.class,
        () -> wallet.debit(uid, 1, WalletService.REASON_USAGE, "usage:outertx-rb-1", null, null));
    assertTrue(users.findSuspendedIds().contains(uid), "无外层事务时扣款拒绝仍在锁内置位停服");
    // 再次入账可恢复停服。
    wallet.credit(uid, 10, WalletService.REASON_MANUAL_TOPUP, "outertx-after-rb", null, null);
    assertFalse(users.findSuspendedIds().contains(uid), "入账后停服解除");
    assertEquals(10, wallet.balance(uid));
  }
}
