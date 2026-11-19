package com.longzhong.jinguan.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.longzhong.jinguan.payment.PaymentProvider.CallbackPayload;
import com.longzhong.jinguan.wallet.WalletService;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** 验证待结算用量的重启恢复、用户轮转、处理上限和资金事件路径。 */
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
class UsageSettlementRecoveryTest extends UsageSettlementTestSupport {

  private static final String KEY_R3 = "mk-r3";
  private static final String KEY_R5 = "mk-r5";
  private static final String KEY_C1 = "mk-c1";
  private static final String KEY_C2 = "mk-c2";
  private static final String REC_C1A = "rec-c1a";

  @Test
  void crashAfterReservePendingRowRecoveredOnDemand() {
    long uid = newUser("rec-u1");
    bindings.save(new ApiKeyBindingEntity("mk-r1", uid));
    // 模拟预留后、扣账前中断。
    usageRecords.save(
        UsageRecordEntity.of(row("rec-1", "mk-r1", 100), uid, UsageRecordService.OUTCOME_PENDING));
    wallet.credit(uid, 1_000, WalletService.REASON_MANUAL_TOPUP, "rec-seed-1", null, null);

    var report = scheduler.recoverOnce();

    assertEquals(1, report.settled(), "重启后首轮恢复应完成补扣");
    var stored = usageRecords.findByUsageId("rec-1").orElseThrow();
    assertEquals(UsageRecordService.OUTCOME_ACCEPTED, stored.getFinalOutcome(), "投影终态应 accepted");
    assertEquals(900, wallet.balance(uid), "补扣 100 后余额 900");
    Assertions.assertTrue(ledger.findByRefId("usage:rec-1").isPresent(), "恰一笔 usage 分录");
    // 恢复完成后再次扫描无待办。
    assertEquals(0, scheduler.recoverOnce().settled(), "恢复完成后不应再有候选");
  }

  @Test
  void crashAfterDebitProjectionRepairedWithoutSecondDebit() {
    long uid = newUser("rec-u2");
    bindings.save(new ApiKeyBindingEntity("mk-r2", uid));
    usageRecords.save(
        UsageRecordEntity.of(row("rec-2", "mk-r2", 100), uid, UsageRecordService.OUTCOME_PENDING));
    wallet.credit(uid, 1_000, WalletService.REASON_MANUAL_TOPUP, "rec-seed-2", null, null);
    // 模拟扣账成功但投影未更新。
    wallet.debit(
        uid, 100, WalletService.REASON_USAGE, IngestService.usageRefId("rec-2"), null, null);

    var report = scheduler.recoverOnce();

    assertEquals(1, report.settled(), "恢复应修复投影");
    var stored = usageRecords.findByUsageId("rec-2").orElseThrow();
    assertEquals(UsageRecordService.OUTCOME_REPLAYED, stored.getFinalOutcome(), "按同一幂等分录修复为已结算");
    assertEquals(900, wallet.balance(uid), "不产生第二笔扣款");
  }

  @Test
  void firstReceiveOrderStopsAtInsufficient() {
    long uid = newUser("rec-u3");
    seedInsufficient(KEY_R3, uid, "rec-3a", 100);
    seedInsufficient(KEY_R3, uid, "rec-3b", 100);
    seedInsufficient(KEY_R3, uid, "rec-3c", 100);
    wallet.credit(uid, 250, WalletService.REASON_MANUAL_TOPUP, "rec-seed-3", null, null);

    // 每用户每轮最多处理两行，按首次接收顺序执行。
    var round1 = scheduler.recoverOnce();
    assertEquals(2, round1.settled(), "余额覆盖前两笔");
    assertEquals(0, round1.stillInsufficient(), "第三行未被触及,不计欠费");
    assertEquals(
        "accepted",
        usageRecords.findByUsageId("rec-3a").orElseThrow().getFinalOutcome(),
        "首行应按接收顺序结算");
    assertEquals(
        "skipped_insufficient",
        usageRecords.findByUsageId("rec-3c").orElseThrow().getFinalOutcome(),
        "未触及行保持欠费终态");
  }

  @Test
  void insufficientCandidateResuspendsAndCountsAttempts() {
    long uid = newUser("rec-u3b");
    seedInsufficient("mk-r3b", uid, "rec-3d", 100);
    seedInsufficient("mk-r3b", uid, "rec-3e", 100);
    seedInsufficient("mk-r3b", uid, "rec-3f", 100);
    wallet.credit(uid, 250, WalletService.REASON_MANUAL_TOPUP, "rec-seed-3b", null, null);
    scheduler.recoverOnce(); // 先消费两笔

    // 余额不足时保留候选并重新停服。
    var round2 = scheduler.recoverOnce();
    assertEquals(0, round2.settled(), "第三笔无法结算");
    assertEquals(1, round2.stillInsufficient(), "第三笔保留为欠费候选");
    assertEquals(50, wallet.balance(uid), "余额保持 50");
    Assertions.assertTrue(users.findSuspendedIds().contains(uid), "恢复失败重新置位业务停服");
    // 每行分别记录接收和恢复尝试。
    Assertions.assertTrue(
        usageRecords.findByUsageId("rec-3d").orElseThrow().getAttempts() == 2
            && usageRecords.findByUsageId("rec-3f").orElseThrow().getAttempts() == 2,
        "每行各记录一次接收与一次恢复尝试");
  }

  @Test
  void userFairnessExitedRowsKeepRotationHistory() throws Exception {
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

    // 首轮处理最早尝试的两个用户。
    var round1 = scheduler.recoverOnce();
    assertEquals(4, round1.settled(), "首轮应结算两个用户各两行");
    Assertions.assertTrue(
        remainingCandidates(u1) == 1 && remainingCandidates(u2) == 1,
        "u1/u2 各剩一行,实际:" + remainingCandidates(u1) + "/" + remainingCandidates(u2));
    assertEquals(3, remainingCandidates(u3), "u3 本轮未触及");
    TimeUnit.MILLISECONDS.sleep(20); // 保证轮转时间戳推进,消除同刻并列

    // 后续轮次按最近尝试时间轮转用户。
    var round2 = scheduler.recoverOnce();
    Assertions.assertTrue(
        remainingCandidates(u3) == 1 && remainingCandidates(u1) + remainingCandidates(u2) == 1,
        "u3 本轮被触及(2 行),另一名额给了有历史的用户");
    Assertions.assertTrue(round2.settled() >= 2, "本轮至少结算两行");
  }

  @Test
  void candidateOrderingNeverAttemptedFirstThenRecentAttemptLast() {
    long attempted = newUser("rec-o1");
    long neverTried = newUser("rec-o2");
    // 对比已有尝试时间和从未尝试的 pending 行。
    seedInsufficient("mk-o1", attempted, "rec-o1a", 10);
    usageRecords.save(
        UsageRecordEntity.of(
            row("rec-o2a", "mk-o2", 10), neverTried, UsageRecordService.OUTCOME_PENDING));
    bindings.save(new ApiKeyBindingEntity("mk-o2", neverTried));

    var order = usageRecords.findCandidateUsers(10);

    Assertions.assertTrue(order.size() >= 2, "两名候选用户均在列");
    Assertions.assertTrue(
        order.indexOf(neverTried) < order.indexOf(attempted), "从未实际尝试的用户必须先于已有尝试历史的用户: " + order);
  }

  @Test
  void conflictedPoisonRowDoesNotBlockOtherUsers() {
    // 模拟 refId 已被不同金额占用；冲突行保留待办且不阻塞其他用户。
    long u1 = newUser("rec-c1");
    long u2 = newUser("rec-c2");
    bindings.save(new ApiKeyBindingEntity(KEY_C1, u1));
    bindings.save(new ApiKeyBindingEntity(KEY_C2, u2));
    usageRecords.save(
        UsageRecordEntity.of(row(REC_C1A, KEY_C1, 100), u1, UsageRecordService.OUTCOME_PENDING));
    usageRecords.save(
        UsageRecordEntity.of(row("rec-c2a", KEY_C2, 100), u2, UsageRecordService.OUTCOME_PENDING));
    wallet.credit(u1, 1_000, WalletService.REASON_MANUAL_TOPUP, "poison-seed-1", null, null);
    wallet.credit(u2, 1_000, WalletService.REASON_MANUAL_TOPUP, "poison-seed-2", null, null);
    // 预置同 refId 的不同金额分录。
    wallet.debit(u1, 50, WalletService.REASON_USAGE, IngestService.usageRefId(REC_C1A), null, null);

    var report = scheduler.recoverOnce();

    assertEquals(1, report.failed(), "毒行计失败");
    assertEquals(1, report.settled(), "其他用户不受阻塞,正常结算");
    assertEquals(
        UsageRecordService.OUTCOME_PENDING,
        usageRecords.findByUsageId(REC_C1A).orElseThrow().getFinalOutcome(),
        "冲突行保留候选");
    assertEquals(
        UsageRecordService.OUTCOME_ACCEPTED,
        usageRecords.findByUsageId("rec-c2a").orElseThrow().getFinalOutcome(),
        "其他用户正常结算");
    assertEquals(950, wallet.balance(u1), "毒行不扣款,占位分录余额保持");

    // 模拟修复异常行，避免污染后续轮转断言。
    usageService.applySettlementOutcome(REC_C1A, UsageRecordService.OUTCOME_REPLAYED);
  }

  @Test
  void unexpectedExceptionRowDoesNotAbortRound() {
    // cost 为 0 的损坏候选应被跳过，不得中断整轮。
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
        usageRecords.findByUsageId("rec-x2a").orElseThrow().getFinalOutcome(),
        "健康行结算为 accepted");
    assertEquals(900, wallet.balance(u2), "健康行扣款后余额 900");

    // 修复后将坏行移出候选。
    usageService.applySettlementOutcome("rec-x1a", UsageRecordService.OUTCOME_REPLAYED);
  }

  @Test
  void unboundAndFreeHistoryNeverAutoCharged() {
    long uid = newUser("rec-u4");
    // 恢复器不得追收未绑定或免费行。
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
    assertEquals(1_000, wallet.balance(uid), "余额不受未绑定/免费历史影响");
    assertEquals(
        "skipped_unbound",
        usageRecords.findByUsageId("rec-4a").orElseThrow().getFinalOutcome(),
        "未绑定行保持跳过终态");
  }

  @Test
  void mockPaymentThenRecoverySettlesHistoryAndKeepsUnsuspended() {
    long uid = newUser("rec-u5");
    seedInsufficient(KEY_R5, uid, "rec-5a", 100);

    // 支付回调在外层事务提交前入账。
    var order = payments.createOrder("mock", uid, 500, "CNY");
    String orderId = (String) order.get("orderId");
    payments.handleCallback("mock", orderId, new CallbackPayload(orderId, "ref-x", "PAID", "sig"));

    var report = scheduler.recoverOnce();

    assertEquals(1, report.settled(), "恢复应补扣历史欠费");
    assertEquals(400, wallet.balance(uid), "500 入账 - 100 补扣");
    Assertions.assertFalse(users.findSuspendedIds().contains(uid), "结清后不再停服");
    assertEquals(
        "accepted",
        usageRecords.findByUsageId("rec-5a").orElseThrow().getFinalOutcome(),
        "欠费行结清为 accepted");
  }
}
