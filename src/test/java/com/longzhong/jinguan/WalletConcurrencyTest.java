package com.longzhong.jinguan.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.longzhong.jinguan.common.InsufficientFundsException;
import com.longzhong.jinguan.ingest.ApiKeyBindingEntity;
import com.longzhong.jinguan.ingest.ApiKeyBindingRepository;
import com.longzhong.jinguan.ingest.IngestService;
import com.longzhong.jinguan.ingest.IngestUsageRow;
import com.longzhong.jinguan.ingest.UsageRecordRepository;
import com.longzhong.jinguan.user.PrincipalEntity;
import com.longzhong.jinguan.user.PrincipalRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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

  private static final String CURRENCY = "CNY";
  private static final String PROVIDER = "deepseek";
  private static final String MODEL = "deepseek-chat";
  private static final String PASSWORD_HASH = "hash";
  private static final String ROLE_USER = "USER";
  private static final String KEY_POOR2 = "mk-poor2";

  @Autowired WalletService wallet;
  @Autowired PrincipalRepository users;
  @Autowired ApiKeyBindingRepository bindings;
  @Autowired IngestService ingest;
  @Autowired UsageRecordRepository usageRecords;

  @Test
  void concurrentDebitsNeverOverdraw() throws Exception {
    var user = users.save(PrincipalEntity.jit("conc-u1", "conc-u1"));
    String subject = user.getSubject();
    wallet.credit(subject, 100_000, WalletService.REASON_MANUAL_TOPUP, "conc-seed", null, null);

    int threads = 20;
    int perDebit = 30_000; // 余额只够 3 笔
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger success = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();

    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      final String ref = "conc-d-" + i;
      futures.add(
          CompletableFuture.runAsync(
              () -> {
                try {
                  latch.await();
                } catch (InterruptedException e) {
                  throw new IllegalStateException("并发等待被中断", e);
                }
                try {
                  wallet.debit(subject, perDebit, WalletService.REASON_USAGE, ref, null, null);
                  success.incrementAndGet();
                } catch (InsufficientFundsException e) {
                  rejected.incrementAndGet();
                }
              }));
    }
    latch.countDown();
    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

    assertEquals(3, success.get(), "成功笔数应恰为余额可覆盖的笔数");
    assertEquals(threads - 3, rejected.get(), "其余应被非负不变量拒绝");
    assertEquals(10_000, wallet.balance(subject), "终态余额 = 100000 - 3×30000");
    assertEquals(threads, success.get() + rejected.get(), "每笔恰好一种结局(无丢失)");
  }

  @Test
  void ingestInsufficientRowSkipped() {
    var user = users.save(PrincipalEntity.jit("conc-u2", "conc-u2"));
    bindings.save(new ApiKeyBindingEntity("mk-poor", user.getSubject()));

    var result = ingest.accept(List.of(expensiveRow("poison-1", "mk-poor")));

    assertEquals(1, result.skippedInsufficient(), "欠费行应跳过");
    assertEquals(0, result.accepted(), "欠费行不得计为接收");
    assertEquals(
        "skipped_insufficient",
        usageRecords.findByUsageId("poison-1").orElseThrow().getFinalOutcome(),
        "欠费明细保留终态");
  }

  @Test
  void ingestGoodRowSettlesAfterEarlierInsufficient() {
    var user = users.save(PrincipalEntity.jit("conc-u2b", "conc-u2b"));
    bindings.save(new ApiKeyBindingEntity(KEY_POOR2, user.getSubject()));

    ingest.accept(List.of(expensiveRow("poison-2", KEY_POOR2)));
    // 先入账，确保后续扣款可成功。
    wallet.credit(
        user.getSubject(), 1_000, WalletService.REASON_MANUAL_TOPUP, "conc-seed-2", null, null);
    var result2 = ingest.accept(List.of(expensiveRow("poison-2", KEY_POOR2), goodRow()));

    assertEquals(1, result2.skippedInsufficient(), "欠费行仍跳过");
    assertEquals(1, result2.accepted(), "后续正常行应完成扣款");
    assertEquals(
        "accepted", usageRecords.findByUsageId("ok-1").orElseThrow().getFinalOutcome(), "正常行终态");
  }

  /** 并发同键写入应收敛为成功与幂等重放。 */
  @Test
  void concurrentSameRefIdCreditsReplayNotFailure() throws Exception {
    var user = users.save(PrincipalEntity.jit("conc-dup", "conc-dup"));
    String subject = user.getSubject();

    int threads = 20;
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger accepted = new AtomicInteger();
    AtomicInteger replayed = new AtomicInteger();

    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      futures.add(
          CompletableFuture.runAsync(
              () -> {
                try {
                  latch.await();
                } catch (InterruptedException e) {
                  throw new IllegalStateException("并发等待被中断", e);
                }
                var r =
                    wallet.credit(
                        subject,
                        50_000,
                        WalletService.REASON_MANUAL_TOPUP,
                        "conc-dup-ref",
                        null,
                        null);
                if (r.replayed()) {
                  replayed.incrementAndGet();
                } else {
                  accepted.incrementAndGet();
                }
              }));
    }
    latch.countDown();
    // 任一任务异常(约束违例裸 500 化)在此即失败
    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

    assertEquals(1, accepted.get(), "恰一笔新写入生效");
    assertEquals(threads - 1, replayed.get(), "其余并发方应幂等重放,不得异常");
    assertEquals(50_000, wallet.balance(subject), "余额只加一次");
  }

  private static IngestUsageRow expensiveRow(String usageId, String keyId) {
    return row(usageId, keyId, 999_999_000, "2026-09-12T00:00:00Z");
  }

  private static IngestUsageRow goodRow() {
    return row("ok-1", KEY_POOR2, 1_000, "2026-09-12T00:00:01Z");
  }

  private static IngestUsageRow row(String usageId, String keyId, long cost, String createdAt) {
    return new IngestUsageRow(
        usageId,
        "r-" + usageId,
        "t1",
        keyId,
        PROVIDER,
        MODEL,
        MODEL,
        false,
        "ok",
        "",
        10,
        5,
        0,
        0,
        false,
        cost,
        CURRENCY,
        null,
        100,
        createdAt);
  }
}
