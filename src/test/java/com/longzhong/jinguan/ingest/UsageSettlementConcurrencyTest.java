package com.longzhong.jinguan.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.longzhong.jinguan.common.MetricNames;
import com.longzhong.jinguan.wallet.WalletService;
import io.micrometer.core.instrument.Tag;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** 验证在线重投与恢复器并发同键收敛,以及恢复路径不伪造接收指标。 */
@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-recover-conc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.settlement.fixed-delay-ms=3600000",
      "jinguan.settlement.initial-delay-ms=3600000",
      "jinguan.settlement.max-users=2",
      "jinguan.settlement.max-rows-per-user=2",
    })
class UsageSettlementConcurrencyTest extends UsageSettlementTestSupport {

  @Test
  void onlineIngestVsRecoveryConcurrentSameRefIdConverges() throws Exception {
    long uid = newUser("rec-u7");
    seedInsufficient("mk-r7", uid, "rec-7", 100); // 候选已落库
    wallet.credit(uid, 1_000, WalletService.REASON_MANUAL_TOPUP, "rec-seed-7", null, null);

    var start = new CountDownLatch(1);
    CompletableFuture<Void> online =
        CompletableFuture.runAsync(
            () -> {
              try {
                start.await();
              } catch (InterruptedException e) {
                throw new IllegalStateException("并发等待被中断", e);
              }
              ingest.accept(List.of(row("rec-7", "mk-r7", 100))); // 数据面重投
            });
    CompletableFuture<Void> recovery =
        CompletableFuture.runAsync(
            () -> {
              try {
                start.await();
              } catch (InterruptedException e) {
                throw new IllegalStateException("并发等待被中断", e);
              }
              scheduler.recoverOnce();
            });
    start.countDown();
    online.get(10, TimeUnit.SECONDS);
    recovery.get(10, TimeUnit.SECONDS);

    var stored = usageRecords.findByUsageId("rec-7").orElseThrow();
    Assertions.assertTrue(
        UsageRecordService.OUTCOME_ACCEPTED.equals(stored.getFinalOutcome())
            || UsageRecordService.OUTCOME_REPLAYED.equals(stored.getFinalOutcome()),
        "投影终态收敛,当前: " + stored.getFinalOutcome());
    assertEquals(900, wallet.balance(uid), "恰一笔扣款");
    Assertions.assertTrue(ledger.findByRefId("usage:rec-7").isPresent(), "refId 恰一分录");
  }

  @Test
  void recoveryDoesNotFakeIngestCounts() {
    long uid = newUser("rec-u6");
    usageRecords.save(
        UsageRecordEntity.of(row("rec-6", "mk-r6", 100), uid, UsageRecordService.OUTCOME_PENDING));
    bindings.save(new ApiKeyBindingEntity("mk-r6", uid));
    wallet.credit(uid, 1_000, WalletService.REASON_MANUAL_TOPUP, "rec-seed-6", null, null);

    final double acceptedBefore = ingestRowCount(MetricNames.OUTCOME_ACCEPTED);
    final double replayedBefore = ingestRowCount(MetricNames.OUTCOME_REPLAYED);
    final double insufficientBefore = ingestRowCount(MetricNames.OUTCOME_SKIPPED_INSUFFICIENT);

    scheduler.recoverOnce();

    assertEquals(acceptedBefore, ingestRowCount(MetricNames.OUTCOME_ACCEPTED), "恢复不得计入接收分类");
    assertEquals(replayedBefore, ingestRowCount(MetricNames.OUTCOME_REPLAYED), "恢复不得计入接收分类");
    assertEquals(
        insufficientBefore, ingestRowCount(MetricNames.OUTCOME_SKIPPED_INSUFFICIENT), "恢复不得计入接收分类");
    // 恢复补扣仍通过钱包资金通道。
    Assertions.assertTrue(
        meters
                .counter(
                    MetricNames.WALLET_OPERATIONS,
                    List.of(
                        Tag.of(MetricNames.TAG_KIND, MetricNames.KIND_DEBIT),
                        Tag.of(MetricNames.TAG_OUTCOME, MetricNames.OUTCOME_APPLIED)))
                .count()
            >= 1.0,
        "恢复扣款计入资金指标");
  }
}
