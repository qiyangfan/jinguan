package com.longzhong.jinguan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 验证指标暴露、资金事件计数和标签基数。
 *
 * <p>注册表跨测试方法累积，因此断言计数增量；资金指标以账本事实为准。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-metrics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "jinguan.security.jwt-secret=test-secret-0123456789abcdef-0123456789abcdef",
      "jinguan.payment.order-ttl-minutes=60",
    })
class OpsMetricsIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired MeterRegistry registry;
  @Autowired com.longzhong.jinguan.user.UserRepository users;
  @Autowired com.longzhong.jinguan.payment.PaymentExpiryScheduler scheduler;
  @Autowired com.longzhong.jinguan.payment.MockOrderRepository orders;

  static String adminToken;

  private static final double EPS = 1e-9;

  /** 未创建的计数序列按 0 处理。 */
  private double counter(String name, String... tags) {
    var found = registry.find(name).tags(tags).counter();
    return found == null ? 0.0 : found.count();
  }

  /** 创建普通用户供账本测试使用。 */
  private long seedUser(String username) {
    return users
        .save(com.longzhong.jinguan.user.UserEntity.create(username, "hash", "USER"))
        .getId();
  }

  private String registerFirstAdmin() throws Exception {
    MvcResult result =
        mvc.perform(
                post("/auth/register")
                    .contentType("application/json")
                    .content("{\"username\":\"ops-admin\",\"password\":\"password-123\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");
  }

  private void credit(long userId, long amount, String refId) throws Exception {
    mvc.perform(
            post("/wallet/admin/credit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(
                    "{\"userId\":"
                        + userId
                        + ",\"amountCredits\":"
                        + amount
                        + ",\"refId\":\""
                        + refId
                        + "\"}"))
        .andExpect(status().isCreated());
  }

  /** 构造与 muniu 字段名一致的用量行。 */
  private static String usageRow(String id, String keyId, long costCredits, String status) {
    return """
        {"ID":"%s","RequestID":"r-%s","TenantID":"t1","APIKeyID":"%s",
         "Provider":"deepseek","ModelAlias":"deepseek-chat","UpstreamModel":"deepseek-chat",
         "Stream":true,"Status":"%s","ErrorClass":"","PromptTokens":100,
         "CompletionTokens":50,"ReasoningTokens":0,"CachedTokens":0,"Estimated":false,
         "CostCredits":%d,"Currency":"CNY","LatencyFirstTokenMs":120,
         "LatencyTotalMs":900,"CreatedAt":"2026-09-30T00:00:00Z"}"""
        .formatted(id, id, keyId, status, costCredits);
  }

  private void ingest(String rowsJsonBody) throws Exception {
    mvc.perform(
            post("/ingest/usage")
                .header("Authorization", "Bearer dev-ingest-token")
                .contentType("application/json")
                .content("{\"rows\":[" + rowsJsonBody + "]}"))
        .andExpect(status().isOk());
  }

  @Test
  @Order(1)
  void 引导_首用户为ADMIN() throws Exception {
    adminToken = registerFirstAdmin();
  }

  @Test
  @Order(2)
  void 账本计数_入账生效与幂等重放分桶() throws Exception {
    double appliedBefore =
        counter("jinguan.wallet.operations", "kind", "credit", "outcome", "applied");
    double replayedBefore =
        counter("jinguan.wallet.operations", "kind", "credit", "outcome", "replayed");

    credit(1, 1_000_000_000L, "ops-credit-1");
    credit(1, 1_000_000_000L, "ops-credit-1"); // 同键同载荷:幂等重放,不加钱

    assertEquals(
        1.0,
        counter("jinguan.wallet.operations", "kind", "credit", "outcome", "applied")
            - appliedBefore,
        EPS,
        "入账生效应计一次 applied");
    assertEquals(
        1.0,
        counter("jinguan.wallet.operations", "kind", "credit", "outcome", "replayed")
            - replayedBefore,
        EPS,
        "同键重放应计一次 replayed(而非再次 applied)");
  }

  @Test
  @Order(3)
  void 账本计数_余额不足与同键冲突可分别告警() throws Exception {
    double rejectedInsufficientBefore =
        counter("jinguan.wallet.operations", "kind", "debit", "outcome", "rejected_insufficient");
    double rejectedConflictBefore =
        counter("jinguan.wallet.operations", "kind", "credit", "outcome", "rejected_conflict");

    long poorUid = seedUser("ops-poor");
    // 余额不足:该用户名下零入账,admin 调账扣减必拒
    mvc.perform(
            post("/wallet/admin/debit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(
                    "{\"userId\":"
                        + poorUid
                        + ",\"amountCredits\":500000000,\"refId\":\"ops-debit-x\"}"))
        .andExpect(status().isUnprocessableEntity());
    assertEquals(
        1.0,
        counter("jinguan.wallet.operations", "kind", "debit", "outcome", "rejected_insufficient")
            - rejectedInsufficientBefore,
        EPS,
        "余额不足拒绝必须单独成桶");

    // 同 refId 不同金额:幂等冲突(账本纪律:同键不同载荷即事故信号)
    mvc.perform(
            post("/wallet/admin/credit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("{\"userId\":1,\"amountCredits\":999,\"refId\":\"ops-credit-1\"}"))
        .andExpect(status().isConflict());
    assertEquals(
        1.0,
        counter("jinguan.wallet.operations", "kind", "credit", "outcome", "rejected_conflict")
            - rejectedConflictBefore,
        EPS,
        "同键不同载荷冲突必须单独成桶");
  }

  @Test
  @Order(4)
  void ingest六桶_与IngestResult同源计数() throws Exception {
    mvc.perform(
            post("/admin/key-bindings")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("{\"keyId\":\"mk-ops-1\",\"userId\":1}"))
        .andExpect(status().isCreated());

    double acceptedBefore = counter("jinguan.ingest.rows", "outcome", "accepted");
    double replayedBefore = counter("jinguan.ingest.rows", "outcome", "replayed");
    double unboundBefore = counter("jinguan.ingest.rows", "outcome", "skipped_unbound");
    double freeBefore = counter("jinguan.ingest.rows", "outcome", "skipped_free");
    double insufficientBefore = counter("jinguan.ingest.rows", "outcome", "skipped_insufficient");

    ingest(usageRow("ops-1", "mk-ops-1", 300_000_000L, "ok"));
    ingest(usageRow("ops-1", "mk-ops-1", 300_000_000L, "ok")); // 同 UsageRow.ID 重投:幂等
    ingest(usageRow("ops-unbound", "mk-no-such-key", 300_000_000L, "ok"));
    ingest(usageRow("ops-free", "mk-ops-1", 0, "ok")); // 零成本行:不扣账
    ingest(usageRow("ops-insufficient", "mk-ops-1", 999_999_999_999L, "ok")); // 扣穿余额

    assertEquals(1.0, counter("jinguan.ingest.rows", "outcome", "accepted") - acceptedBefore, EPS);
    assertEquals(1.0, counter("jinguan.ingest.rows", "outcome", "replayed") - replayedBefore, EPS);
    assertEquals(
        1.0, counter("jinguan.ingest.rows", "outcome", "skipped_unbound") - unboundBefore, EPS);
    assertEquals(1.0, counter("jinguan.ingest.rows", "outcome", "skipped_free") - freeBefore, EPS);
    assertEquals(
        1.0,
        counter("jinguan.ingest.rows", "outcome", "skipped_insufficient") - insufficientBefore,
        EPS);
  }

  @Test
  @Order(5)
  void 订单计数_创建与置付且过期不双计() throws Exception {
    double createdBefore = counter("jinguan.payment.orders", "event", "created");
    double paidBefore = counter("jinguan.payment.orders", "event", "paid");
    double convergedBefore = counter("jinguan.payment.expiry.converged");

    MvcResult order =
        mvc.perform(
                post("/payments/orders")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json")
                    .content(
                        "{\"provider\":\"mock\",\"amountCredits\":2000000000,\"currency\":\"CNY\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String orderId =
        com.jayway.jsonpath.JsonPath.read(order.getResponse().getContentAsString(), "$.orderId");

    double creditAppliedBefore =
        counter("jinguan.wallet.operations", "kind", "credit", "outcome", "applied");
    double creditReplayedBefore =
        counter("jinguan.wallet.operations", "kind", "credit", "outcome", "replayed");

    mvc.perform(post("/payments/mock/{id}/pay", orderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replayed").value(false));
    mvc.perform(post("/payments/mock/{id}/pay", orderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replayed").value(true)); // 重推回调:不重复计 paid

    // 回调入账参与外层事务，计数应在提交后生效。
    assertEquals(
        1.0,
        counter("jinguan.wallet.operations", "kind", "credit", "outcome", "applied")
            - creditAppliedBefore,
        EPS,
        "置付入账须经 afterCommit 计入 wallet{credit,applied}");
    // 回调重放不发布入账事件，也不产生账本计数。
    assertEquals(
        0.0,
        counter("jinguan.wallet.operations", "kind", "credit", "outcome", "replayed")
            - creditReplayedBefore,
        EPS,
        "重推回调不得产生任何账本操作计数");

    assertEquals(1.0, counter("jinguan.payment.orders", "event", "created") - createdBefore, EPS);
    assertEquals(1.0, counter("jinguan.payment.orders", "event", "paid") - paidBefore, EPS);

    // 支付成功不应触发过期收敛。
    assertEquals(counter("jinguan.payment.expiry.converged"), convergedBefore, EPS, "本用例未触发收敛");
  }

  @Test
  @Order(6)
  void 管子活性_整包与304分桶且管理页读不计数() throws Exception {
    double pricingFullBefore =
        counter("jinguan.control.pull", "pipe", "pricing", "outcome", "full");
    double revocationsFullBefore =
        counter("jinguan.control.pull", "pipe", "key_revocations", "outcome", "full");
    double topoFullBefore = counter("jinguan.control.pull", "pipe", "topology", "outcome", "full");
    double topoNotModifiedBefore =
        counter("jinguan.control.pull", "pipe", "topology", "outcome", "not_modified");

    mvc.perform(get("/control/v1/pricing")).andExpect(status().isOk());
    mvc.perform(get("/control/v1/key-revocations")).andExpect(status().isOk());
    MvcResult topo =
        mvc.perform(get("/control/v1/topology")).andExpect(status().isOk()).andReturn();
    String etag = topo.getResponse().getHeader("ETag");
    assertNotNull(etag, "topology 首响应应带 ETag");
    mvc.perform(get("/control/v1/topology").header("If-None-Match", etag))
        .andExpect(status().isNotModified());

    assertEquals(
        1.0,
        counter("jinguan.control.pull", "pipe", "pricing", "outcome", "full") - pricingFullBefore,
        EPS);
    assertEquals(
        1.0,
        counter("jinguan.control.pull", "pipe", "key_revocations", "outcome", "full")
            - revocationsFullBefore,
        EPS);
    assertEquals(
        1.0,
        counter("jinguan.control.pull", "pipe", "topology", "outcome", "full") - topoFullBefore,
        EPS);
    assertEquals(
        1.0,
        counter("jinguan.control.pull", "pipe", "topology", "outcome", "not_modified")
            - topoNotModifiedBefore,
        EPS);

    // 管理端读取不计为数据面拉取。
    double afterAdminRead = counter("jinguan.control.pull", "pipe", "topology", "outcome", "full");
    mvc.perform(get("/admin/topology").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
    assertEquals(
        afterAdminRead,
        counter("jinguan.control.pull", "pipe", "topology", "outcome", "full"),
        EPS);
  }

  @Test
  @Order(7)
  void 低基数_多用户入账不产生新时间序列() throws Exception {
    int before = registry.find("jinguan.wallet.operations").counters().size();
    long second = seedUser("ops-second");
    credit(1, 10_000_000L, "ops-multi-1");
    credit(second, 10_000_000L, "ops-multi-2");
    int after = registry.find("jinguan.wallet.operations").counters().size();

    assertEquals(before, after, "序列数不得随用户数增长(标签集合封闭)");
    assertTrue(
        registry.find("jinguan.wallet.operations").counters().stream()
            .flatMap(c -> c.getId().getTags().stream())
            .map(Tag::getKey)
            .allMatch(k -> com.longzhong.jinguan.common.MetricNames.WALLET_TAG_KEYS.contains(k)),
        "账本指标标签只允许 MetricNames.WALLET_TAG_KEYS 列出的键");
  }

  @Test
  @Order(8)
  @Transactional // 直接调用扫描器时事务由测试提供(与 PaymentExpiryTest 同法)
  void 积压Gauge与扫描器同源且过期只由既有序列承载() {
    var gauge = registry.find("jinguan.payment.expiry.backlog").gauge();
    assertNotNull(gauge, "backlog 必须注册为 Gauge");

    long uid = seedUser("ops-exp-u");
    Instant past = Instant.now().minusSeconds(1);
    for (String orderId : List.of("po-ops-exp-1", "po-ops-exp-2", "po-ops-exp-3")) {
      orders.save(
          com.longzhong.jinguan.payment.MockOrderEntity.create(orderId, uid, 500000, "CNY", past));
    }
    double convergedBefore = counter("jinguan.payment.expiry.converged");

    assertEquals(
        3L,
        orders.countByStatusAndExpiresAtBefore("CREATED", Instant.now()),
        "种子必须是 3 笔已逾期仍 CREATED,否则本用例什么都没验");
    // Gauge 表示最近扫描结果，不应实时反映新插入的订单。
    assertTrue(gauge.value() < 3.0, "Gauge 不得实时反映 DB 存量(那是另一种口径),实际读数:" + gauge.value());

    scheduler.expireOverdueOrders();

    assertEquals(
        3.0,
        counter("jinguan.payment.expiry.converged") - convergedBefore,
        EPS,
        "三笔过期单必须全部由既有 converged 序列承载");
    assertEquals(0.0, gauge.value(), EPS, "本轮扫完积压归零");
    assertEquals(scheduler.backlog(), gauge.value(), EPS, "Gauge 与扫描器状态同源");

    // 过期订单只由 expiry.converged 序列统计。
    assertNull(
        registry.find("jinguan.payment.orders").tag("event", "expired").counter(),
        "orders 指标不得出现 expired 桶(过期事实由 expiry.converged 单承载)");
  }

  @Test
  @Order(9)
  void 批内后续行冲突不得吞掉已提交行的归宿计数() throws Exception {
    // 每行独立提交；后续行冲突时，前序行的计数仍须保留。
    double acceptedBefore = counter("jinguan.ingest.rows", "outcome", "accepted");

    mvc.perform(
            post("/ingest/usage")
                .header("Authorization", "Bearer dev-ingest-token")
                .contentType("application/json")
                .content(
                    "{\"rows\":["
                        + usageRow("ops-pin-9", "mk-ops-1", 100_000_000L, "ok")
                        + ","
                        + usageRow("ops-1", "mk-ops-1", 400_000_000L, "ok")
                        + "]}"))
        .andExpect(status().isConflict());

    assertEquals(
        1.0,
        counter("jinguan.ingest.rows", "outcome", "accepted") - acceptedBefore,
        EPS,
        "第 2 行抛冲突时,第 1 行已提交的 accepted 必须已经计入");
  }
}
