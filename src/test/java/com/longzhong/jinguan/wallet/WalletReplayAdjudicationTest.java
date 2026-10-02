package com.longzhong.jinguan.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.longzhong.jinguan.common.IdempotencyConflictException;
import com.longzhong.jinguan.common.InsufficientFundsException;
import com.longzhong.jinguan.user.UserEntity;
import com.longzhong.jinguan.user.UserRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 幂等裁决序回归(wallet-ledger delta):refId 既有分录的重放/冲突必须先于新扣款余额判断——
 * 已扣分录在低余额下重投应得重放,不得误报欠费/停服;同键不同载荷在低余额下仍为冲突。
 */
@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-replay;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
    })
class WalletReplayAdjudicationTest {

  @Autowired WalletService wallet;
  @Autowired UserRepository users;
  @Autowired LedgerEntryRepository ledger;

  private long newUser(String username) {
    return users.save(UserEntity.create(username, "hash", "USER")).getId();
  }

  @Test
  void lowBalance_sameRefId_replays_withoutSuspension() {
    long uid = newUser("replay-u1");
    wallet.credit(uid, 100, WalletService.REASON_MANUAL_TOPUP, "replay-seed-1", null, null);
    wallet.debit(uid, 60, WalletService.REASON_USAGE, "usage:replay-r1", null, null); // 余额 100→40

    // 同键同载荷重投:当前余额 40 < 60,仍必须返回重放而非欠费
    var result = wallet.debit(uid, 60, WalletService.REASON_USAGE, "usage:replay-r1", null, null);

    assertTrue(result.replayed(), "低余额下已扣分录重投应返回重放");
    assertEquals(40, wallet.balance(uid), "重放不改变余额");
    assertTrue(ledger.findByRefId("usage:replay-r1").isPresent(), "同 refId 分录存在");
    assertFalse(users.findSuspendedIds().contains(uid), "合法重放不得置位业务停服");
  }

  @Test
  void lowBalance_conflictingPayload_remainsConflict() {
    long uid = newUser("replay-u2");
    wallet.credit(uid, 100, WalletService.REASON_MANUAL_TOPUP, "replay-seed-2", null, null);
    wallet.debit(uid, 60, WalletService.REASON_USAGE, "usage:replay-r2", null, null); // 余额 40

    // 同 refId 不同金额:即便余额不足(50 > 40),也必须报幂等冲突,不伪装成新欠费
    assertThrows(
        IdempotencyConflictException.class,
        () -> wallet.debit(uid, 50, WalletService.REASON_USAGE, "usage:replay-r2", null, null));
    assertEquals(40, wallet.balance(uid), "冲突不新增分录");
    assertFalse(users.findSuspendedIds().contains(uid), "冲突不置位停服");
  }

  @Test
  void concurrent_sameKey_converges_replaySemantics() throws Exception {
    long uid = newUser("replay-u3");
    wallet.credit(uid, 60, WalletService.REASON_MANUAL_TOPUP, "replay-seed-3", null, null);
    // 余额恰等于单笔金额:并发同键下,先到者扣款成功,后到者必须重放(而非裸 500 或欠费)
    int threads = 12;
    var latch = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(threads);
    var replayed = new AtomicInteger();
    var rejected = new AtomicInteger();
    var conflicts = new AtomicInteger();
    List<Future<?>> futures = new java.util.ArrayList<>();
    for (int i = 0; i < threads; i++) {
      futures.add(
          pool.submit(
              () -> {
                latch.await();
                try {
                  var r =
                      wallet.debit(
                          uid, 60, WalletService.REASON_USAGE, "usage:replay-r3", null, null);
                  if (r.replayed()) {
                    replayed.incrementAndGet();
                  }
                } catch (InsufficientFundsException e) {
                  rejected.incrementAndGet();
                } catch (IdempotencyConflictException e) {
                  conflicts.incrementAndGet();
                }
                return null;
              }));
    }
    latch.countDown();
    for (Future<?> f : futures) {
      f.get();
    }
    pool.shutdown();

    assertEquals(threads - 1, replayed.get(), "除首笔外全部收敛为重放");
    assertEquals(0, rejected.get(), "并发同键同载荷不得误报欠费");
    assertEquals(0, conflicts.get(), "同载荷不得误报冲突");
    assertEquals(0, wallet.balance(uid), "终态余额 = 60 - 60");
    assertTrue(ledger.findByRefId("usage:replay-r3").isPresent(), "同 refId 分录存在");
  }
}
