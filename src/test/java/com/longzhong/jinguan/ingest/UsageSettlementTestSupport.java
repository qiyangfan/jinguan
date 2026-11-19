package com.longzhong.jinguan.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.longzhong.jinguan.common.MetricNames;
import com.longzhong.jinguan.payment.PaymentService;
import com.longzhong.jinguan.user.UserEntity;
import com.longzhong.jinguan.user.UserRepository;
import com.longzhong.jinguan.wallet.LedgerEntryRepository;
import com.longzhong.jinguan.wallet.WalletService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

/** 结算恢复测试的共享夹具与候选清理,避免测试类方法数超标。 */
class UsageSettlementTestSupport {

  private static final long DRAIN_MIN_BALANCE = 10_000_000L;

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

  long newUser(String username) {
    return users.save(UserEntity.create(username, "hash", "USER")).getId();
  }

  IngestUsageRow row(String id, String keyId, long cost) {
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

  /** 创建一笔归属已固定的欠费候选。 */
  void seedInsufficient(String keyId, long uid, String id, long cost) {
    bindings.save(new ApiKeyBindingEntity(keyId, uid));
    var result = ingest.accept(List.of(row(id, keyId, cost)));
    assertEquals(1, result.skippedInsufficient(), "种子行应为欠费留存");
  }

  long remainingCandidates(long userId) {
    return usageRecords.findCandidateRows(userId, 1000).size();
  }

  double ingestRowCount(String outcome) {
    return meters.counter(MetricNames.INGEST_ROWS, "outcome", outcome).count();
  }

  /** 清理候选行，避免影响其他轮转断言。 */
  @AfterEach
  void drainCandidates() {
    for (int i = 0; i < 20; i++) {
      var pending = usageRecords.findCandidateUsers(100);
      if (pending.isEmpty()) {
        return;
      }
      for (Long uid : pending) {
        if (wallet.balance(uid) < DRAIN_MIN_BALANCE) {
          wallet.credit(
              uid,
              DRAIN_MIN_BALANCE,
              WalletService.REASON_MANUAL_TOPUP,
              "drain-" + uid + "-" + i,
              null,
              null);
        }
      }
      scheduler.recoverOnce();
    }
  }
}
