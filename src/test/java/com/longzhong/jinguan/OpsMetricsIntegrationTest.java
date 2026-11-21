package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.longzhong.jinguan.common.MetricNames;
import io.micrometer.core.instrument.Tag;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-metrics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "jinguan.security.jwt-secret=test-secret-0123456789abcdef-0123456789abcdef",
      "jinguan.payment.order-ttl-minutes=60",
    })
class OpsMetricsIntegrationTest extends OpsMetricsTestSupport {

  private static final double EPS = 1e-9;
  private static final String KEY_OPS = "mk-ops-1";

  @BeforeAll
  void registerAdmin() throws Exception {
    adminToken = registerFirstAdmin();
    Assertions.assertNotNull(adminToken, "首用户注册必须取得 token");
  }

  @Test
  @Order(1)
  void ledgerCountsAppliedAndReplayedBuckets() throws Exception {
    final double appliedBefore =
        counter(
            MetricNames.WALLET_OPERATIONS,
            MetricNames.TAG_KIND,
            MetricNames.KIND_CREDIT,
            MetricNames.TAG_OUTCOME,
            MetricNames.OUTCOME_APPLIED);
    final double replayedBefore =
        counter(
            MetricNames.WALLET_OPERATIONS,
            MetricNames.TAG_KIND,
            MetricNames.KIND_CREDIT,
            MetricNames.TAG_OUTCOME,
            MetricNames.OUTCOME_REPLAYED);

    String opsSubject = seedUser("ops-main");
    credit(opsSubject, 1_000_000_000L, "ops-credit-1");
    credit(opsSubject, 1_000_000_000L, "ops-credit-1"); // 同键同载荷:幂等重放,不加钱

    Assertions.assertEquals(
        1.0,
        counter(
                MetricNames.WALLET_OPERATIONS,
                MetricNames.TAG_KIND,
                MetricNames.KIND_CREDIT,
                MetricNames.TAG_OUTCOME,
                MetricNames.OUTCOME_APPLIED)
            - appliedBefore,
        EPS,
        "入账生效应计一次 applied");
    Assertions.assertEquals(
        1.0,
        counter(
                MetricNames.WALLET_OPERATIONS,
                MetricNames.TAG_KIND,
                MetricNames.KIND_CREDIT,
                MetricNames.TAG_OUTCOME,
                MetricNames.OUTCOME_REPLAYED)
            - replayedBefore,
        EPS,
        "同键重放应计一次 replayed(而非再次 applied)");

    final double rejectedInsufficientBefore =
        counter(
            MetricNames.WALLET_OPERATIONS,
            MetricNames.TAG_KIND,
            MetricNames.KIND_DEBIT,
            MetricNames.TAG_OUTCOME,
            MetricNames.OUTCOME_REJECTED_INSUFFICIENT);
    final double rejectedConflictBefore =
        counter(
            MetricNames.WALLET_OPERATIONS,
            MetricNames.TAG_KIND,
            MetricNames.KIND_CREDIT,
            MetricNames.TAG_OUTCOME,
            MetricNames.OUTCOME_REJECTED_CONFLICT);
    String poorSubject = seedUser("ops-poor");
    mvc.perform(
        post("/wallet/admin/debit")
            .header(AUTHORIZATION, BEARER + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"subject\":\""
                    + poorSubject
                    + "\",\"amountCredits\":500000000,\"refId\":\"ops-debit-x\"}"));
    Assertions.assertEquals(
        1.0,
        counter(
                MetricNames.WALLET_OPERATIONS,
                MetricNames.TAG_KIND,
                MetricNames.KIND_DEBIT,
                MetricNames.TAG_OUTCOME,
                MetricNames.OUTCOME_REJECTED_INSUFFICIENT)
            - rejectedInsufficientBefore,
        EPS,
        "余额不足拒绝必须单独成桶");
    mvc.perform(
            post("/wallet/admin/credit")
                .header(AUTHORIZATION, BEARER + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"subject\":\"sub-ops-main\",\"amountCredits\":999,"
                        + "\"refId\":\"ops-credit-1\"}"))
        .andExpect(status().isConflict());
    Assertions.assertEquals(
        1.0,
        counter(
                MetricNames.WALLET_OPERATIONS,
                MetricNames.TAG_KIND,
                MetricNames.KIND_CREDIT,
                MetricNames.TAG_OUTCOME,
                MetricNames.OUTCOME_REJECTED_CONFLICT)
            - rejectedConflictBefore,
        EPS,
        "同键不同载荷冲突必须单独成桶");
  }

  @Test
  @Order(2)
  void ingestSixOutcomeBucketsMatchResult() throws Exception {
    mvc.perform(
            post("/admin/key-bindings")
                .header(AUTHORIZATION, BEARER + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"keyId\":\"" + KEY_OPS + "\",\"subject\":\"sub-ops-main\"}"))
        .andExpect(status().isCreated());

    final double acceptedBefore =
        counter(MetricNames.INGEST_ROWS, MetricNames.TAG_OUTCOME, MetricNames.OUTCOME_ACCEPTED);
    final double replayedBefore =
        counter(MetricNames.INGEST_ROWS, MetricNames.TAG_OUTCOME, MetricNames.OUTCOME_REPLAYED);
    final double unboundBefore =
        counter(
            MetricNames.INGEST_ROWS, MetricNames.TAG_OUTCOME, MetricNames.OUTCOME_SKIPPED_UNBOUND);
    final double freeBefore =
        counter(MetricNames.INGEST_ROWS, MetricNames.TAG_OUTCOME, MetricNames.OUTCOME_SKIPPED_FREE);
    final double insufficientBefore =
        counter(
            MetricNames.INGEST_ROWS,
            MetricNames.TAG_OUTCOME,
            MetricNames.OUTCOME_SKIPPED_INSUFFICIENT);

    ingest(usageRow("ops-1", KEY_OPS, 300_000_000L, "ok"));
    ingest(usageRow("ops-1", KEY_OPS, 300_000_000L, "ok")); // 同 UsageRow.ID 重投:幂等
    ingest(usageRow("ops-unbound", "mk-no-such-key", 300_000_000L, "ok"));
    ingest(usageRow("ops-free", KEY_OPS, 0, "ok")); // 零成本行:不扣账
    ingest(usageRow("ops-insufficient", KEY_OPS, 999_999_999_999L, "ok")); // 扣穿余额

    Assertions.assertEquals(
        1.0,
        counter(MetricNames.INGEST_ROWS, MetricNames.TAG_OUTCOME, MetricNames.OUTCOME_ACCEPTED)
            - acceptedBefore,
        EPS,
        "accepted 桶应计一次");
    Assertions.assertEquals(
        1.0,
        counter(MetricNames.INGEST_ROWS, MetricNames.TAG_OUTCOME, MetricNames.OUTCOME_REPLAYED)
            - replayedBefore,
        EPS,
        "replayed 桶应计一次");
    Assertions.assertEquals(
        1.0,
        counter(
                MetricNames.INGEST_ROWS,
                MetricNames.TAG_OUTCOME,
                MetricNames.OUTCOME_SKIPPED_UNBOUND)
            - unboundBefore,
        EPS,
        "未绑定桶应计一次");
    Assertions.assertEquals(
        1.0,
        counter(MetricNames.INGEST_ROWS, MetricNames.TAG_OUTCOME, MetricNames.OUTCOME_SKIPPED_FREE)
            - freeBefore,
        EPS,
        "免费桶应计一次");
    Assertions.assertEquals(
        1.0,
        counter(
                MetricNames.INGEST_ROWS,
                MetricNames.TAG_OUTCOME,
                MetricNames.OUTCOME_SKIPPED_INSUFFICIENT)
            - insufficientBefore,
        EPS,
        "欠费桶应计一次");
  }

  @Test
  @Order(3)
  void paymentOrdersCreatedPaidWithoutDoubleCount() throws Exception {
    final double createdBefore =
        counter(MetricNames.PAYMENT_ORDERS, MetricNames.TAG_EVENT, MetricNames.EVENT_CREATED);
    final double paidBefore =
        counter(MetricNames.PAYMENT_ORDERS, MetricNames.TAG_EVENT, MetricNames.EVENT_PAID);
    final double convergedBefore = counter(MetricNames.PAYMENT_EXPIRY_CONVERGED);

    MvcResult order =
        mvc.perform(
                post("/payments/orders")
                    .header(AUTHORIZATION, BEARER + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"provider":"mock","amountCredits":2000000000,"currency":"CNY"}"""))
            .andReturn();
    String orderId = JsonPath.read(order.getResponse().getContentAsString(), "$.orderId");

    final double creditAppliedBefore =
        counter(
            MetricNames.WALLET_OPERATIONS,
            MetricNames.TAG_KIND,
            MetricNames.KIND_CREDIT,
            MetricNames.TAG_OUTCOME,
            MetricNames.OUTCOME_APPLIED);
    final double creditReplayedBefore =
        counter(
            MetricNames.WALLET_OPERATIONS,
            MetricNames.TAG_KIND,
            MetricNames.KIND_CREDIT,
            MetricNames.TAG_OUTCOME,
            MetricNames.OUTCOME_REPLAYED);

    mvc.perform(post("/payments/mock/{id}/pay", orderId));
    mvc.perform(post("/payments/mock/{id}/pay", orderId)); // 重推回调:不重复计 paid

    // 回调入账参与外层事务，计数应在提交后生效。
    Assertions.assertEquals(
        1.0,
        counter(
                MetricNames.WALLET_OPERATIONS,
                MetricNames.TAG_KIND,
                MetricNames.KIND_CREDIT,
                MetricNames.TAG_OUTCOME,
                MetricNames.OUTCOME_APPLIED)
            - creditAppliedBefore,
        EPS,
        "置付入账须经 afterCommit 计入 wallet{credit,applied}");
    // 回调重放不发布入账事件，也不产生账本计数。
    Assertions.assertEquals(
        0.0,
        counter(
                MetricNames.WALLET_OPERATIONS,
                MetricNames.TAG_KIND,
                MetricNames.KIND_CREDIT,
                MetricNames.TAG_OUTCOME,
                MetricNames.OUTCOME_REPLAYED)
            - creditReplayedBefore,
        EPS,
        "重推回调不得产生任何账本操作计数");
    Assertions.assertEquals(
        1.0,
        counter(MetricNames.PAYMENT_ORDERS, MetricNames.TAG_EVENT, MetricNames.EVENT_CREATED)
            - createdBefore,
        EPS,
        "下单计数应 +1");
    Assertions.assertEquals(
        1.0,
        counter(MetricNames.PAYMENT_ORDERS, MetricNames.TAG_EVENT, MetricNames.EVENT_PAID)
            - paidBefore,
        EPS,
        "置付计数应 +1");
    // 支付成功不应触发过期收敛。
    Assertions.assertEquals(
        convergedBefore, counter(MetricNames.PAYMENT_EXPIRY_CONVERGED), EPS, "本用例未触发收敛");
  }

  @Test
  @Order(4)
  void controlPullCountsFullPerPipe() throws Exception {
    final double pricingFullBefore =
        counter(
            MetricNames.CONTROL_PULL,
            MetricNames.TAG_PIPE,
            MetricNames.PIPE_PRICING,
            MetricNames.TAG_OUTCOME,
            MetricNames.OUTCOME_FULL);
    final double revocationsFullBefore =
        counter(
            MetricNames.CONTROL_PULL,
            MetricNames.TAG_PIPE,
            MetricNames.PIPE_KEY_REVOCATIONS,
            MetricNames.TAG_OUTCOME,
            MetricNames.OUTCOME_FULL);
    final double topoFullBefore =
        counter(
            MetricNames.CONTROL_PULL,
            MetricNames.TAG_PIPE,
            MetricNames.PIPE_TOPOLOGY,
            MetricNames.TAG_OUTCOME,
            MetricNames.OUTCOME_FULL);

    mvc.perform(get("/control/v1/pricing"));
    mvc.perform(get("/control/v1/key-revocations"));
    mvc.perform(get("/control/v1/topology"));

    Assertions.assertEquals(
        1.0,
        counter(
                MetricNames.CONTROL_PULL,
                MetricNames.TAG_PIPE,
                MetricNames.PIPE_PRICING,
                MetricNames.TAG_OUTCOME,
                MetricNames.OUTCOME_FULL)
            - pricingFullBefore,
        EPS,
        "价目拉取应计一次 full");
    Assertions.assertEquals(
        1.0,
        counter(
                MetricNames.CONTROL_PULL,
                MetricNames.TAG_PIPE,
                MetricNames.PIPE_KEY_REVOCATIONS,
                MetricNames.TAG_OUTCOME,
                MetricNames.OUTCOME_FULL)
            - revocationsFullBefore,
        EPS,
        "吊销拉取应计一次 full");
    Assertions.assertEquals(
        1.0,
        counter(
                MetricNames.CONTROL_PULL,
                MetricNames.TAG_PIPE,
                MetricNames.PIPE_TOPOLOGY,
                MetricNames.TAG_OUTCOME,
                MetricNames.OUTCOME_FULL)
            - topoFullBefore,
        EPS,
        "拓扑拉取应计一次 full");
  }

  @Test
  @Order(5)
  void topo304NotModifiedAndAdminReadNotCounted() throws Exception {
    MvcResult topo = mvc.perform(get("/control/v1/topology")).andReturn();
    String etag = topo.getResponse().getHeader("ETag");
    Assertions.assertNotNull(etag, "topology 首响应应带 ETag");
    final double notModifiedBefore =
        counter(
            MetricNames.CONTROL_PULL,
            MetricNames.TAG_PIPE,
            MetricNames.PIPE_TOPOLOGY,
            MetricNames.TAG_OUTCOME,
            MetricNames.OUTCOME_NOT_MODIFIED);
    mvc.perform(get("/control/v1/topology").header("If-None-Match", etag));

    Assertions.assertEquals(
        1.0,
        counter(
                MetricNames.CONTROL_PULL,
                MetricNames.TAG_PIPE,
                MetricNames.PIPE_TOPOLOGY,
                MetricNames.TAG_OUTCOME,
                MetricNames.OUTCOME_NOT_MODIFIED)
            - notModifiedBefore,
        EPS,
        "命中 If-None-Match 应计一次 not_modified");

    // 管理端读取不计为数据面拉取。
    double afterAdminRead =
        counter(
            MetricNames.CONTROL_PULL,
            MetricNames.TAG_PIPE,
            MetricNames.PIPE_TOPOLOGY,
            MetricNames.TAG_OUTCOME,
            MetricNames.OUTCOME_FULL);
    mvc.perform(get("/admin/topology").header(AUTHORIZATION, BEARER + adminToken));
    Assertions.assertEquals(
        afterAdminRead,
        counter(
            MetricNames.CONTROL_PULL,
            MetricNames.TAG_PIPE,
            MetricNames.PIPE_TOPOLOGY,
            MetricNames.TAG_OUTCOME,
            MetricNames.OUTCOME_FULL),
        EPS,
        "管理页读取不得计入数据面拉取");
  }

  @Test
  @Order(6)
  void lowCardinalityNoNewSeriesPerUser() throws Exception {
    int before = registry.find(MetricNames.WALLET_OPERATIONS).counters().size();
    String second = seedUser("ops-second");
    credit("sub-ops-main", 10_000_000L, "ops-multi-1");
    credit(second, 10_000_000L, "ops-multi-2");
    int after = registry.find(MetricNames.WALLET_OPERATIONS).counters().size();

    Assertions.assertEquals(before, after, "序列数不得随用户数增长(标签集合封闭)");
    Assertions.assertTrue(
        registry.find(MetricNames.WALLET_OPERATIONS).counters().stream()
            .flatMap(c -> c.getId().getTags().stream())
            .map(Tag::getKey)
            .allMatch(MetricNames.WALLET_TAG_KEYS::contains),
        "账本指标标签只允许 MetricNames.WALLET_TAG_KEYS 列出的键");
  }

  @Test
  @Order(7)
  @Transactional // 直接调用扫描器时事务由测试提供(与 PaymentExpiryTest 同法)
  void backlogGaugeIsSnapshotNotRealtime() {
    final var gauge = registry.find(MetricNames.PAYMENT_EXPIRY_BACKLOG).gauge();
    Assertions.assertNotNull(gauge, "backlog 必须注册为 Gauge");

    String subject = seedUser("ops-exp-u");
    Instant past = Instant.now().minusSeconds(1);
    for (String orderId : List.of("po-ops-exp-1", "po-ops-exp-2")) {
      orders.save(
          com.longzhong.jinguan.payment.MockOrderEntity.create(
              orderId, subject, 500_000, "CNY", past));
    }

    Assertions.assertEquals(
        2L,
        orders.countByStatusAndExpiresAtBefore("CREATED", Instant.now()),
        "种子必须是 2 笔已逾期仍 CREATED,否则本用例什么都没验");
    // Gauge 表示最近扫描结果，不应实时反映新插入的订单。
    Assertions.assertTrue(gauge.value() < 2.0, "Gauge 不得实时反映 DB 存量(那是另一种口径),实际读数:" + gauge.value());
  }

  @Test
  @Order(8)
  @Transactional
  void expiredOrdersOnlyCountedByConverged() {
    final var gauge = registry.find(MetricNames.PAYMENT_EXPIRY_BACKLOG).gauge();
    String subject = seedUser("ops-exp-u2");
    Instant past = Instant.now().minusSeconds(1);
    for (String orderId : List.of("po-ops-run-1", "po-ops-run-2")) {
      orders.save(
          com.longzhong.jinguan.payment.MockOrderEntity.create(
              orderId, subject, 500_000, "CNY", past));
    }
    final double convergedBefore = counter(MetricNames.PAYMENT_EXPIRY_CONVERGED);

    scheduler.expireOverdueOrders();

    Assertions.assertEquals(
        2.0,
        counter(MetricNames.PAYMENT_EXPIRY_CONVERGED) - convergedBefore,
        EPS,
        "两笔过期单必须全部由既有 converged 序列承载");
    Assertions.assertEquals(0.0, gauge.value(), EPS, "本轮扫完积压归零");
    Assertions.assertEquals(scheduler.backlog(), gauge.value(), EPS, "Gauge 与扫描器状态同源");
    // 过期订单只由 expiry.converged 序列统计。
    Assertions.assertNull(
        registry.find(MetricNames.PAYMENT_ORDERS).tag(MetricNames.TAG_EVENT, "expired").counter(),
        "orders 指标不得出现 expired 桶(过期事实由 expiry.converged 单承载)");
  }

  @Test
  @Order(9)
  void laterRowConflictKeepsCommittedRowOutcome() throws Exception {
    // 每行独立提交；后续行冲突时，前序行的计数仍须保留。
    final double acceptedBefore =
        counter(MetricNames.INGEST_ROWS, MetricNames.TAG_OUTCOME, MetricNames.OUTCOME_ACCEPTED);

    mvc.perform(
            post("/ingest/usage")
                .header(AUTHORIZATION, BEARER + DEV_INGEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"rows\":["
                        + usageRow("ops-pin-9", KEY_OPS, 100_000_000L, "ok")
                        + ","
                        + usageRow("ops-1", KEY_OPS, 400_000_000L, "ok")
                        + "]}"))
        .andExpect(status().isConflict());

    Assertions.assertEquals(
        1.0,
        counter(MetricNames.INGEST_ROWS, MetricNames.TAG_OUTCOME, MetricNames.OUTCOME_ACCEPTED)
            - acceptedBefore,
        EPS,
        "第 2 行抛冲突时,第 1 行已提交的 accepted 必须已经计入");
  }
}
