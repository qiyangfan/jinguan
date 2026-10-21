package com.longzhong.jinguan.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.longzhong.jinguan.common.InsufficientFundsException;
import com.longzhong.jinguan.ingest.ApiKeyBindingEntity;
import com.longzhong.jinguan.ingest.ApiKeyBindingRepository;
import com.longzhong.jinguan.ingest.IngestService;
import com.longzhong.jinguan.ingest.IngestUsageRow;
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

/** 验证并发扣款不超扣，且单行 ingest 失败不阻断整批。 */
@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-conc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
    })
class WalletConcurrencyTest {

  @Autowired WalletService wallet;
  @Autowired UserRepository users;
  @Autowired ApiKeyBindingRepository bindings;
  @Autowired IngestService ingest;
  @Autowired com.longzhong.jinguan.ingest.UsageRecordRepository usageRecords;

  @Test
  void concurrent_debits_never_overdraw() throws Exception {
    var user = users.save(UserEntity.create("conc-u1", "hash", "USER"));
    long uid = user.getId();
    wallet.credit(uid, 100000, WalletService.REASON_MANUAL_TOPUP, "conc-seed", null, null);

    int threads = 20, perDebit = 30000; // 余额只够 3 笔
    var latch = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(threads);
    var success = new AtomicInteger();
    var rejected = new AtomicInteger();
    var futures = new java.util.ArrayList<Future<?>>();

    for (int i = 0; i < threads; i++) {
      final String ref = "conc-d-" + i;
      futures.add(
          pool.submit(
              () -> {
                latch.await();
                try {
                  wallet.debit(uid, perDebit, WalletService.REASON_USAGE, ref, null, null);
                  success.incrementAndGet();
                } catch (InsufficientFundsException e) {
                  rejected.incrementAndGet();
                }
                return null;
              }));
    }
    latch.countDown();
    for (Future<?> f : futures) {
      f.get();
    }
    pool.shutdown();

    assertEquals(3, success.get(), "成功笔数应恰为余额可覆盖的笔数");
    assertEquals(threads - 3, rejected.get(), "其余应被非负不变量拒绝");
    assertEquals(10000, wallet.balance(uid), "终态余额 = 100000 - 3×30000");
    assertEquals(threads, success.get() + rejected.get(), "每笔恰好一种结局(无丢失)");
  }

  @Test
  void ingest_insufficientRow_skipped_notPoison() {
    var user = users.save(UserEntity.create("conc-u2", "hash", "USER"));
    bindings.save(new ApiKeyBindingEntity("mk-poor", user.getId()));

    var row =
        new IngestUsageRow(
            "poison-1",
            "r-1",
            "t1",
            "mk-poor",
            "deepseek",
            "deepseek-chat",
            "deepseek-chat",
            false,
            "ok",
            "",
            10,
            5,
            0,
            0,
            false,
            999999000,
            "CNY",
            null,
            100,
            "2026-09-12T00:00:00Z");
    var result = ingest.accept(List.of(row));

    assertEquals(1, result.skippedInsufficient());
    assertEquals(0, result.accepted());
    // 失败行不应阻断后续正常行。
    var good =
        new IngestUsageRow(
            "ok-1",
            "r-2",
            "t1",
            "mk-poor",
            "deepseek",
            "deepseek-chat",
            "deepseek-chat",
            false,
            "ok",
            "",
            1,
            1,
            0,
            0,
            false,
            1000,
            "CNY",
            null,
            50,
            "2026-09-12T00:00:01Z");
    // 先入账，确保后续扣款可成功。
    wallet.credit(user.getId(), 1000, WalletService.REASON_MANUAL_TOPUP, "conc-seed-2", null, null);
    var result2 = ingest.accept(List.of(row, good));
    assertEquals(1, result2.skippedInsufficient());
    assertEquals(1, result2.accepted());
    // 欠费明细保留，正常行完成扣款。
    assertEquals(
        "skipped_insufficient",
        usageRecords.findByUsageId("poison-1").orElseThrow().getFinalOutcome());
    assertEquals("accepted", usageRecords.findByUsageId("ok-1").orElseThrow().getFinalOutcome());
  }

  /** 并发同键写入应收敛为成功与幂等重放。 */
  @Test
  void concurrent_sameRefId_credits_replay_notFailure() throws Exception {
    var user = users.save(UserEntity.create("conc-dup", "hash", "USER"));
    long uid = user.getId();

    int threads = 20;
    var latch = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(threads);
    var accepted = new AtomicInteger();
    var replayed = new AtomicInteger();
    var futures = new java.util.ArrayList<Future<?>>();

    for (int i = 0; i < threads; i++) {
      futures.add(
          pool.submit(
              () -> {
                latch.await();
                var r =
                    wallet.credit(
                        uid, 50000, WalletService.REASON_MANUAL_TOPUP, "conc-dup-ref", null, null);
                if (r.replayed()) {
                  replayed.incrementAndGet();
                } else {
                  accepted.incrementAndGet();
                }
                return null;
              }));
    }
    latch.countDown();
    for (Future<?> f : futures) {
      f.get(); // 任一线程抛异常(约束违例裸 500 化)在此即失败
    }
    pool.shutdown();

    assertEquals(1, accepted.get(), "恰一笔新写入生效");
    assertEquals(threads - 1, replayed.get(), "其余并发方应幂等重放,不得异常");
    assertEquals(50000, wallet.balance(uid), "余额只加一次");
  }
}
