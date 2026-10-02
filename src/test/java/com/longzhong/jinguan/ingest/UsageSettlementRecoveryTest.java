package com.longzhong.jinguan.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.longzhong.jinguan.common.MetricNames;
import com.longzhong.jinguan.payment.PaymentProvider.CallbackPayload;
import com.longzhong.jinguan.payment.PaymentService;
import com.longzhong.jinguan.user.UserEntity;
import com.longzhong.jinguan.user.UserRepository;
import com.longzhong.jinguan.wallet.LedgerEntryRepository;
import com.longzhong.jinguan.wallet.WalletService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 待结算恢复器(ingest-metering delta ADDED):可重启且有界——预留后中断/扣账后投影中断/ 重启漏处理由周期扫描覆盖;用户间公平轮转(成功行退出候选不重置历史);
 * 同用户首收顺序、余额不足即止;后台恢复不伪造 ingest 接收计数;Mock 支付与管理员入账同走统一恢复。
 */
@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-recover;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.settlement.fixed-delay-ms=3600000", // 测试全部走 recoverOnce 驱动入口,禁真实调度
      "jinguan.settlement.initial-delay-ms=3600000",
      "jinguan.settlement.max-users=2",
      "jinguan.settlement.max-rows-per-user=2",
    })
class UsageSettlementRecoveryTest {

  @Autowired UsageSettlementScheduler scheduler;
  @Autowired IngestService ingest;
  @Autowired UsageRecordRepository usageRecords;
  @Autowired UsageRecordService usageService;
  @Autowired ApiKeyBindingRepository bindings;
  @Autowired UserRepository users;
  @Autowired WalletService wallet;
  @Autowired LedgerEntryRepository ledger;
  @Autowired PaymentService payments;
  @Autowired MeterRegistry meters;

  private long newUser(String username) {
    return users.save(UserEntity.create(username, "hash", "USER")).getId();
  }

  private IngestUsageRow row(String id, String keyId, long cost) {
    return new IngestUsageRow(
        id,
        "req-" + id,
        "t1",
        keyId,
        "deepseek",
        "deepseek-chat",
        "deepseek-v4-pro",
        false,
        "ok",
        null,
        10,
        20,
        0,
        0,
        false,
        cost,
        "CNY",
        120L,
        300L,
        "2026-10-02T00:00:00Z");
  }

  /** 造一笔欠费候选(u 余额 0):行留存为 skipped_insufficient、归属固定。 */
  private void seedInsufficient(String keyId, long uid, String id, long cost) {
    bindings.save(new ApiKeyBindingEntity(keyId, uid));
    var result = ingest.accept(List.of(row(id, keyId, cost)));
    assertEquals(1, result.skippedInsufficient(), "种子行应为欠费留存");
  }

  @Test
  void crashAfterReserve_pendingRow_recoveredOnDemand() {
    long uid = newUser("rec-u1");
    bindings.save(new ApiKeyBindingEntity("mk-r1", uid));
    // 模拟「预留后、扣账前」进程中断:直接落 pending 明细(等价于 ingest 中断现场)
    usageRecords.save(
        UsageRecordEntity.of(row("rec-1", "mk-r1", 100), uid, UsageRecordService.OUTCOME_PENDING));
    wallet.credit(uid, 1_000, WalletService.REASON_MANUAL_TOPUP, "rec-seed-1", null, null);

    var report = scheduler.recoverOnce();

    assertEquals(1, report.settled(), "重启后首轮恢复应完成补扣");
    var stored = usageRecords.findByUsageId("rec-1").orElseThrow();
    assertEquals(UsageRecordService.OUTCOME_ACCEPTED, stored.getFinalOutcome());
    assertEquals(900, wallet.balance(uid));
    assertTrue(ledger.findByRefId("usage:rec-1").isPresent(), "恰一笔 usage 分录");
    // 收敛完成:后续轮次无事可做(等价重启后再扫)
    assertEquals(0, scheduler.recoverOnce().settled());
  }

  @Test
  void crashAfterDebit_projectionRepaired_withoutSecondDebit() {
    long uid = newUser("rec-u2");
    bindings.save(new ApiKeyBindingEntity("mk-r2", uid));
    usageRecords.save(
        UsageRecordEntity.of(row("rec-2", "mk-r2", 100), uid, UsageRecordService.OUTCOME_PENDING));
    wallet.credit(uid, 1_000, WalletService.REASON_MANUAL_TOPUP, "rec-seed-2", null, null);
    // 模拟「扣账成功、投影未落」中断:资金通道先扣,明细仍 pending
    wallet.debit(
        uid, 100, WalletService.REASON_USAGE, IngestService.usageRefId("rec-2"), null, null);

    var report = scheduler.recoverOnce();

    assertEquals(1, report.settled(), "恢复应修复投影");
    var stored = usageRecords.findByUsageId("rec-2").orElseThrow();
    assertEquals(UsageRecordService.OUTCOME_REPLAYED, stored.getFinalOutcome(), "按同一幂等分录修复为已结算");
    assertEquals(900, wallet.balance(uid), "不产生第二笔扣款");
  }

  @Test
  void sameUser_firstReceiveOrder_stopAtInsufficient() {
    long uid = newUser("rec-u3");
    seedInsufficient("mk-r3", uid, "rec-3a", 100);
    seedInsufficient("mk-r3", uid, "rec-3b", 100);
    seedInsufficient("mk-r3", uid, "rec-3c", 100);
    wallet.credit(uid, 250, WalletService.REASON_MANUAL_TOPUP, "rec-seed-3", null, null);

    // 本类 caps=2 用户×2 行:首轮只触及前两行(首收顺序),第三行未触及
    var round1 = scheduler.recoverOnce();
    assertEquals(2, round1.settled(), "余额覆盖前两笔");
    assertEquals(0, round1.stillInsufficient(), "第三行未被触及,不计欠费");
    assertEquals("accepted", usageRecords.findByUsageId("rec-3a").orElseThrow().getFinalOutcome());
    assertEquals("accepted", usageRecords.findByUsageId("rec-3b").orElseThrow().getFinalOutcome());
    assertEquals(
        "skipped_insufficient",
        usageRecords.findByUsageId("rec-3c").orElseThrow().getFinalOutcome());

    // 次轮触及第三行:余额 50 < 100 → 保留并重新停服
    var round2 = scheduler.recoverOnce();
    assertEquals(0, round2.settled());
    assertEquals(1, round2.stillInsufficient(), "第三笔保留");
    assertEquals(50, wallet.balance(uid));
    assertTrue(users.findSuspendedIds().contains(uid), "恢复失败重新置位业务停服");
    // 每行每轮恰计一次恢复尝试(预留 1 + 恢复 1)
    assertEquals(2, usageRecords.findByUsageId("rec-3a").orElseThrow().getAttempts());
    assertEquals(2, usageRecords.findByUsageId("rec-3c").orElseThrow().getAttempts());
  }

  @Test
  void userFairness_exitedRowsKeepRotationHistory() throws Exception {
    long u1 = newUser("rec-f1");
    long u2 = newUser("rec-f2");
    long u3 = newUser("rec-f3");
    for (int i = 0; i < 3; i++) {
      seedInsufficient("mk-f1", u1, "rec-f1-" + i, 10);
      seedInsufficient("mk-f2", u2, "rec-f2-" + i, 10);
      seedInsufficient("mk-f3", u3, "rec-f3-" + i, 10);
    }
    wallet.credit(u1, 1_000, WalletService.REASON_MANUAL_TOPUP, "fair-seed-1", null, null);
    wallet.credit(u2, 1_000, WalletService.REASON_MANUAL_TOPUP, "fair-seed-2", null, null);
    wallet.credit(u3, 1_000, WalletService.REASON_MANUAL_TOPUP, "fair-seed-3", null, null);

    // 第 1 轮(上限 2 用户 × 2 行):最早尝试的 u1、u2 各结 2 行,u3 未触及
    var round1 = scheduler.recoverOnce();
    assertEquals(4, round1.settled());
    assertEquals(1, remainingCandidates(u1));
    assertEquals(1, remainingCandidates(u2));
    assertEquals(3, remainingCandidates(u3), "u3 本轮未触及");
    Thread.sleep(20); // 保证轮转时间戳推进,消除同刻并列

    // 第 2 轮:u1/u2 的尝试历史(含已退出候选的成功行)把二者排到队尾,u3 先被触及
    var round2 = scheduler.recoverOnce();
    assertEquals(1, remainingCandidates(u3), "u3 本轮被触及(2 行)");
    assertEquals(1, remainingCandidates(u1) + remainingCandidates(u2), "另一名额给了有历史的用户");
    assertTrue(round2.settled() >= 2);
  }

  @Test
  void candidateOrdering_neverAttemptedFirst_thenRecentAttemptLast() {
    long attempted = newUser("rec-o1");
    long neverTried = newUser("rec-o2");
    // attempted:经 ingest 欠费(有结算尝试时间);neverTried:预留后中断的 pending(从未尝试)
    seedInsufficient("mk-o1", attempted, "rec-o1a", 10);
    usageRecords.save(
        UsageRecordEntity.of(
            row("rec-o2a", "mk-o2", 10), neverTried, UsageRecordService.OUTCOME_PENDING));
    bindings.save(new ApiKeyBindingEntity("mk-o2", neverTried));

    var order = usageRecords.findCandidateUsers(10);

    assertTrue(order.size() >= 2, "两名候选用户均在列");
    assertTrue(
        order.indexOf(neverTried) < order.indexOf(attempted), "从未实际尝试的用户必须先于已有尝试历史的用户: " + order);
  }

  @Test
  void conflictedPoisonRow_doesNotBlockOtherUsers() {
    // 数据不一致现场:候选行的 refId 已被不同金额分录占用(绕过 reserve 直插,模拟历史坏数据)——
    // 恢复器遇幂等冲突应保留候选、高噪留痕,且不阻塞同轮其他用户结算
    long u1 = newUser("rec-c1");
    long u2 = newUser("rec-c2");
    bindings.save(new ApiKeyBindingEntity("mk-c1", u1));
    bindings.save(new ApiKeyBindingEntity("mk-c2", u2));
    usageRecords.save(
        UsageRecordEntity.of(row("rec-c1a", "mk-c1", 100), u1, UsageRecordService.OUTCOME_PENDING));
    usageRecords.save(
        UsageRecordEntity.of(row("rec-c2a", "mk-c2", 100), u2, UsageRecordService.OUTCOME_PENDING));
    wallet.credit(u1, 1_000, WalletService.REASON_MANUAL_TOPUP, "poison-seed-1", null, null);
    wallet.credit(u2, 1_000, WalletService.REASON_MANUAL_TOPUP, "poison-seed-2", null, null);
    // 占位分录:同 refId、不同金额 → 恢复器 debit 必然 adjudicate 冲突
    wallet.debit(
        u1, 50, WalletService.REASON_USAGE, IngestService.usageRefId("rec-c1a"), null, null);

    var report = scheduler.recoverOnce();

    assertEquals(1, report.failed(), "毒行计失败");
    assertEquals(1, report.settled(), "其他用户不受阻塞,正常结算");
    assertEquals(
        UsageRecordService.OUTCOME_PENDING,
        usageRecords.findByUsageId("rec-c1a").orElseThrow().getFinalOutcome(),
        "冲突行保留候选");
    assertEquals(
        UsageRecordService.OUTCOME_ACCEPTED,
        usageRecords.findByUsageId("rec-c2a").orElseThrow().getFinalOutcome());
    assertEquals(950, wallet.balance(u1), "毒行不扣款,占位分录余额保持");

    // 清场:毒行永久驻留候选会污染共享库的轮转断言——模拟运维修复(投影置终态)退出候选
    usageService.applySettlementOutcome("rec-c1a", UsageRecordService.OUTCOME_REPLAYED);
  }

  @Test
  void unexpectedExceptionRow_doesNotAbortRound() {
    // 坏数据现场:pending 行 cost=0(绕过 reserve 直插)→ debit 金额校验抛 IllegalArgumentException——
    // 恢复器须兜底跳过该行,不得炸掉整轮导致其他用户永久饥饿
    long u1 = newUser("rec-x1");
    long u2 = newUser("rec-x2");
    bindings.save(new ApiKeyBindingEntity("mk-x1", u1));
    bindings.save(new ApiKeyBindingEntity("mk-x2", u2));
    usageRecords.save(
        UsageRecordEntity.of(row("rec-x1a", "mk-x1", 0), u1, UsageRecordService.OUTCOME_PENDING));
    usageRecords.save(
        UsageRecordEntity.of(row("rec-x2a", "mk-x2", 100), u2, UsageRecordService.OUTCOME_PENDING));
    wallet.credit(u2, 1_000, WalletService.REASON_MANUAL_TOPUP, "x-seed-2", null, null);

    var report = scheduler.recoverOnce();

    assertEquals(1, report.failed(), "坏行计失败不炸轮");
    assertEquals(1, report.settled(), "其他用户正常结算");
    assertEquals(
        UsageRecordService.OUTCOME_ACCEPTED,
        usageRecords.findByUsageId("rec-x2a").orElseThrow().getFinalOutcome());
    assertEquals(900, wallet.balance(u2));

    // 清场:坏行退出候选(模拟运维修复),避免污染共享库轮转
    usageService.applySettlementOutcome("rec-x1a", UsageRecordService.OUTCOME_REPLAYED);
  }

  /** 用例间清场:补足候选用户余额并推恢复轮直至收敛,避免跨用例候选污染共享库的轮转断言。 */
  @org.junit.jupiter.api.AfterEach
  void drainCandidates() {
    for (int i = 0; i < 20; i++) {
      var pending = usageRecords.findCandidateUsers(100);
      if (pending.isEmpty()) {
        return;
      }
      for (Long uid : pending) {
        if (wallet.balance(uid) < 10_000_000L) {
          wallet.credit(
              uid,
              10_000_000L,
              WalletService.REASON_MANUAL_TOPUP,
              "drain-" + uid + "-" + i,
              null,
              null);
        }
      }
      scheduler.recoverOnce();
    }
  }

  @Test
  void unboundAndFreeHistory_neverAutoCharged() {
    long uid = newUser("rec-u4");
    // 未绑定历史 + 免费行:恢复器不得追收
    ingest.accept(List.of(row("rec-4a", "mk-none", 100)));
    ingest.accept(
        List.of(
            new IngestUsageRow(
                "rec-4b",
                "rq",
                "t1",
                "mk-none2",
                "p",
                "m",
                null,
                false,
                "ok",
                null,
                1,
                1,
                0,
                0,
                false,
                0,
                "CNY",
                null,
                1L,
                "2026-10-02T00:00:00Z")));
    wallet.credit(uid, 1_000, WalletService.REASON_MANUAL_TOPUP, "rec-seed-4", null, null);
    bindings.save(new ApiKeyBindingEntity("mk-none", uid));

    var report = scheduler.recoverOnce();

    assertEquals(0, report.settled(), "未绑定/免费历史不是候选");
    assertEquals(1_000, wallet.balance(uid));
    assertEquals(
        "skipped_unbound", usageRecords.findByUsageId("rec-4a").orElseThrow().getFinalOutcome());
  }

  @Test
  void mockPayment_thenRecovery_settlesHistory_andKeepsUnsuspended() {
    long uid = newUser("rec-u5");
    seedInsufficient("mk-r5", uid, "rec-5a", 100);
    assertTrue(users.findSuspendedIds().contains(uid), "欠费已停服");

    // Mock 支付:下单 → 回调(BEFORE_COMMIT 入账参与外层事务,锁持有到物理提交)
    var order = payments.createOrder("mock", uid, 500, "CNY");
    String orderId = (String) order.get("orderId");
    payments.handleCallback("mock", orderId, new CallbackPayload(orderId, "ref-x", "PAID", "sig"));
    assertFalse(users.findSuspendedIds().contains(uid), "入账即恢复");

    var report = scheduler.recoverOnce();

    assertEquals(1, report.settled());
    assertEquals(400, wallet.balance(uid), "500 入账 - 100 补扣");
    assertFalse(users.findSuspendedIds().contains(uid), "结清后不再停服");
    assertEquals("accepted", usageRecords.findByUsageId("rec-5a").orElseThrow().getFinalOutcome());
  }

  @Test
  void recovery_doesNotFakeIngestCounts() {
    long uid = newUser("rec-u6");
    usageRecords.save(
        UsageRecordEntity.of(row("rec-6", "mk-r6", 100), uid, UsageRecordService.OUTCOME_PENDING));
    bindings.save(new ApiKeyBindingEntity("mk-r6", uid));
    wallet.credit(uid, 1_000, WalletService.REASON_MANUAL_TOPUP, "rec-seed-6", null, null);

    double acceptedBefore = ingestRowCount(MetricNames.OUTCOME_ACCEPTED);
    double replayedBefore = ingestRowCount(MetricNames.OUTCOME_REPLAYED);
    double insufficientBefore = ingestRowCount(MetricNames.OUTCOME_SKIPPED_INSUFFICIENT);

    scheduler.recoverOnce();

    assertEquals(acceptedBefore, ingestRowCount(MetricNames.OUTCOME_ACCEPTED), "恢复不得计入接收分类");
    assertEquals(replayedBefore, ingestRowCount(MetricNames.OUTCOME_REPLAYED), "恢复不得计入接收分类");
    assertEquals(
        insufficientBefore, ingestRowCount(MetricNames.OUTCOME_SKIPPED_INSUFFICIENT), "恢复不得计入接收分类");
    // 资金事实仍由唯一资金通道承载:恢复补扣计入 wallet.operations
    assertTrue(
        meters
                .counter(
                    MetricNames.WALLET_OPERATIONS,
                    List.of(Tag.of("kind", "debit"), Tag.of("outcome", "applied")))
                .count()
            >= 1.0,
        "恢复扣款计入资金指标");
  }

  @Test
  void onlineIngest_vsRecovery_concurrent_sameRefIdConverges() throws Exception {
    long uid = newUser("rec-u7");
    seedInsufficient("mk-r7", uid, "rec-7", 100); // 候选已落库
    wallet.credit(uid, 1_000, WalletService.REASON_MANUAL_TOPUP, "rec-seed-7", null, null);

    var start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    AtomicReference<Throwable> ingestError = new AtomicReference<>();
    Future<?> online =
        pool.submit(
            () -> {
              try {
                start.await();
                ingest.accept(List.of(row("rec-7", "mk-r7", 100))); // 数据面重投
              } catch (Throwable t) {
                ingestError.set(t);
              }
              return null;
            });
    Future<?> recovery =
        pool.submit(
            () -> {
              try {
                start.await();
                scheduler.recoverOnce();
              } catch (Throwable t) {
                // ingestError 只登记在线侧;恢复侧异常以断言投影终态兜底
              }
              return null;
            });
    start.countDown();
    online.get(10, TimeUnit.SECONDS);
    recovery.get(10, TimeUnit.SECONDS);
    pool.shutdown();

    assertNull(ingestError.get(), "在线重投不得失败");
    var stored = usageRecords.findByUsageId("rec-7").orElseThrow();
    assertTrue(
        UsageRecordService.OUTCOME_ACCEPTED.equals(stored.getFinalOutcome())
            || UsageRecordService.OUTCOME_REPLAYED.equals(stored.getFinalOutcome()),
        "投影终态收敛,当前: " + stored.getFinalOutcome());
    assertEquals(900, wallet.balance(uid), "恰一笔扣款");
    assertTrue(ledger.findByRefId("usage:rec-7").isPresent(), "refId 恰一分录");
  }

  private long remainingCandidates(long userId) {
    return usageRecords.findCandidateRows(userId, 1000).size();
  }

  private double ingestRowCount(String outcome) {
    return meters.counter(MetricNames.INGEST_ROWS, "outcome", outcome).count();
  }
}
