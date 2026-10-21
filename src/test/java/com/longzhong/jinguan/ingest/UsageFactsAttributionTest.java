package com.longzhong.jinguan.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.longzhong.jinguan.common.IdempotencyConflictException;
import com.longzhong.jinguan.user.UserEntity;
import com.longzhong.jinguan.user.UserRepository;
import com.longzhong.jinguan.wallet.WalletService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 验证用量事实不可变、首次归属固定、终态不降级及可靠保存要求。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-facts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
    })
class UsageFactsAttributionTest {

  @Autowired MockMvc mvc;
  @Autowired IngestService ingest;
  @Autowired UsageRecordRepository usageRecords;
  @Autowired UsageRecordService usageService;
  @Autowired ApiKeyBindingRepository bindings;
  @Autowired UserRepository users;
  @Autowired WalletService wallet;

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

  @Test
  void sameId_differentBillingFacts_rejected_beforeFunds() {
    long uid = newUser("facts-u1");
    bindings.save(new ApiKeyBindingEntity("mk-f1", uid));
    wallet.credit(uid, 1_000, WalletService.REASON_MANUAL_TOPUP, "facts-seed-1", null, null);

    var first = ingest.accept(List.of(row("fact-1", "mk-f1", 100)));
    assertEquals(1, first.accepted());

    // 同 ID 不同费用返回冲突，且不改变原事实。
    assertThrows(
        IdempotencyConflictException.class,
        () -> ingest.accept(List.of(row("fact-1", "mk-f1", 200))));
    var stored = usageRecords.findByUsageId("fact-1").orElseThrow();
    assertEquals(100, stored.getCostCredits(), "旧计费事实不可改写");
    assertEquals(UsageRecordService.OUTCOME_ACCEPTED, stored.getFinalOutcome());
    assertEquals(900, wallet.balance(uid), "冲突不产生第二笔扣款");
  }

  @Test
  void keyRebind_historicalRow_staysWithOriginalUser() {
    long u1 = newUser("facts-u2a");
    long u2 = newUser("facts-u2b");
    bindings.save(new ApiKeyBindingEntity("mk-f2", u1));
    // 欠费用量留存并固定归属。
    ingest.accept(List.of(row("fact-2", "mk-f2", 100)));

    // key 重绑后，旧用量仍归属原用户。
    bindings.save(new ApiKeyBindingEntity("mk-f2", u2));
    wallet.credit(u1, 1_000, WalletService.REASON_MANUAL_TOPUP, "facts-seed-2", null, null);
    var result = ingest.accept(List.of(row("fact-2", "mk-f2", 100)));

    assertEquals(1, result.replayed() + result.accepted(), "旧行应完成结算(重放或新扣)");
    var stored = usageRecords.findByUsageId("fact-2").orElseThrow();
    assertEquals(u1, stored.getUserId(), "重绑不得转嫁既有用量归属");
    assertEquals(900, wallet.balance(u1), "向原归属用户结算");
    assertEquals(0, wallet.balance(u2), "不得向新绑定用户扣款");
  }

  @Test
  void settledProjection_neverDowngraded() {
    long uid = newUser("facts-u3");
    bindings.save(new ApiKeyBindingEntity("mk-f3", uid));
    wallet.credit(uid, 1_000, WalletService.REASON_MANUAL_TOPUP, "facts-seed-3", null, null);
    ingest.accept(List.of(row("fact-3", "mk-f3", 100)));

    // 已结算状态不可降级。
    usageService.applySettlementOutcome("fact-3", UsageRecordService.OUTCOME_SKIPPED_INSUFFICIENT);
    usageService.applySettlementOutcome("fact-3", UsageRecordService.OUTCOME_PENDING);
    var stored = usageRecords.findByUsageId("fact-3").orElseThrow();
    assertEquals(UsageRecordService.OUTCOME_ACCEPTED, stored.getFinalOutcome(), "accepted 不降级");

    // replayed 终态同样不可降级。
    usageService.applySettlementOutcome("fact-3", UsageRecordService.OUTCOME_REPLAYED);
    usageService.applySettlementOutcome("fact-3", UsageRecordService.OUTCOME_SKIPPED_INSUFFICIENT);
    stored = usageRecords.findByUsageId("fact-3").orElseThrow();
    assertNotEquals(
        UsageRecordService.OUTCOME_SKIPPED_INSUFFICIENT, stored.getFinalOutcome(), "终态不降级");
  }

  @Test
  void unboundRow_redelivered_whileStillUnbound_keepsNullAttribution() {
    // 未绑定重投不创建新行，attempts 加一且归属仍为空。
    ingest.accept(List.of(row("fact-6", "mk-f6", 100)));
    var second = ingest.accept(List.of(row("fact-6", "mk-f6", 100)));
    assertEquals(1, second.skippedUnbound(), "仍按未绑定跳过");
    var stored = usageRecords.findByUsageId("fact-6").orElseThrow();
    assertNull(stored.getUserId(), "未解析归属前 user_id 保持空");
    assertEquals(UsageRecordService.OUTCOME_SKIPPED_UNBOUND, stored.getFinalOutcome());
    assertEquals(2, stored.getAttempts(), "重投恰计一次");
    assertEquals(
        1, usageRecords.findAll().stream().filter(r -> "fact-6".equals(r.getUsageId())).count());
  }

  @Test
  void reserveFailure_isNon2xx_andLeavesNoFundsEntry() throws Exception {
    long uid = newUser("facts-u4");
    bindings.save(new ApiKeyBindingEntity("mk-f4", uid));
    wallet.credit(uid, 1_000, WalletService.REASON_MANUAL_TOPUP, "facts-seed-4", null, null);

    // 超长 usage_id 无法保存时返回非 2xx，且不新增账本分录。
    String longId = "x".repeat(65);
    mvc.perform(
            post("/ingest/usage")
                .header("Authorization", "Bearer dev-ingest-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    ("{\"rows\":[{\"ID\":\""
                            + longId
                            + "\",\"RequestID\":\"r\",\"APIKeyID\":\"mk-f4\",\"Provider\":\"p\","
                            + "\"ModelAlias\":\"m\",\"Stream\":false,\"Status\":\"ok\",\"ErrorClass\":\"\","
                            + "\"PromptTokens\":1,"
                            + "\"CompletionTokens\":1,\"ReasoningTokens\":0,\"CachedTokens\":0,"
                            + "\"Estimated\":false,\"CostCredits\":100,\"Currency\":\"CNY\",\"LatencyTotalMs\":10}]}")
                        .getBytes(StandardCharsets.UTF_8)))
        .andExpect(status().is5xxServerError());
    assertTrue(usageRecords.findAll().stream().noneMatch(r -> longId.equals(r.getUsageId())));
    assertEquals(1_000, wallet.balance(uid), "未保存的行不得扣款");
  }

  @Test
  void unboundRow_keepsOutcomeUntilRedelivered_afterBinding() {
    long uid = newUser("facts-u5");
    // key 未绑定时保留未绑定明细。
    ingest.accept(List.of(row("fact-5", "mk-f5", 100)));
    var stored = usageRecords.findByUsageId("fact-5").orElseThrow();
    assertEquals(UsageRecordService.OUTCOME_SKIPPED_UNBOUND, stored.getFinalOutcome());

    // 补绑定不会追收历史行。
    bindings.save(new ApiKeyBindingEntity("mk-f5", uid));
    wallet.credit(uid, 1_000, WalletService.REASON_MANUAL_TOPUP, "facts-seed-5", null, null);
    stored = usageRecords.findByUsageId("fact-5").orElseThrow();
    assertEquals(UsageRecordService.OUTCOME_SKIPPED_UNBOUND, stored.getFinalOutcome(), "未重投不自动追收");
    assertNull(stored.getUserId(), "未解析归属前 user_id 保持空");

    // 重投后解析归属并结算。
    var result = ingest.accept(List.of(row("fact-5", "mk-f5", 100)));
    assertEquals(1, result.accepted());
    stored = usageRecords.findByUsageId("fact-5").orElseThrow();
    assertEquals(uid, stored.getUserId());
    assertEquals(UsageRecordService.OUTCOME_ACCEPTED, stored.getFinalOutcome());
    assertEquals(900, wallet.balance(uid));
  }
}
