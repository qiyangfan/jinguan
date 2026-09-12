package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 演示闭环集成测试(单进程,H2):注册(首用户 ADMIN)→ 手工入账 → mock 支付 → 账本入账 → key 绑定 → ingest 扣账 → 余额核对;随行钉价目下发契约格式。
 * 方法按序执行共享状态(引导链路)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "jinguan.security.jwt-secret=test-secret-0123456789abcdef-0123456789abcdef",
    })
class DemoLoopIntegrationTest {

  @Autowired MockMvc mvc;

  static String adminToken;

  private static String body(String json) {
    return json;
  }

  @Test
  @Order(1)
  void register_firstUser_becomesAdmin() throws Exception {
    MvcResult result =
        mvc.perform(
                post("/auth/register")
                    .contentType("application/json")
                    .content(
                        body(
                            """
                            {"username":"solvela","password":"password-123"}""")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").exists())
            .andReturn();
    adminToken =
        com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");
  }

  @Test
  @Order(2)
  void manual_credit_gives_balance() throws Exception {
    mvc.perform(
            post("/wallet/admin/credit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(
                    body(
                        """
                            {"userId":1,"amountMicros":10000000,"refId":"seed-1","note":"种子资金"}""")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.balanceMicros").value(10000000));
  }

  @Test
  @Order(3)
  void manual_credit_sameRefId_replays_withoutDoubleCredit() throws Exception {
    mvc.perform(
            post("/wallet/admin/credit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(
                    body(
                        """
                            {"userId":1,"amountMicros":10000000,"refId":"seed-1"}""")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.balanceMicros").value(10000000)); // 重放不加钱
  }

  @Test
  @Order(4)
  void mockPayment_loop_entersLedger() throws Exception {
    // 下单 → 模拟支付(同事务入账) → 余额 = 10_000_000 + 2_000_000
    MvcResult order =
        mvc.perform(
                post("/payments/orders")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json")
                    .content(
                        body(
                            """
                            {"provider":"mock","amountMicros":2000000,"currency":"CNY"}""")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payUrl").exists())
            .andReturn();
    String orderId =
        com.jayway.jsonpath.JsonPath.read(order.getResponse().getContentAsString(), "$.orderId");

    mvc.perform(post("/payments/mock/{id}/pay", orderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replayed").value(false));
    mvc.perform(get("/wallet/balance").header("Authorization", "Bearer " + adminToken))
        .andExpect(jsonPath("$.balanceMicros").value(12000000));
  }

  @Test
  @Order(5)
  void ingest_usageRow_debits_byKeyBinding() throws Exception {
    mvc.perform(
            post("/admin/key-bindings")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(
                    body(
                        """
                            {"keyId":"mk-test-1","userId":1}""")))
        .andExpect(status().isCreated());
    // ⚠ PascalCase:muniu UsageRow 无 json tag,Go 按导出字段名序列化
    String payload =
        """
            {"rows":[{"ID":"u-1","RequestID":"r-1","TenantID":"t1","APIKeyID":"mk-test-1",
              "Provider":"deepseek","ModelAlias":"deepseek-chat","UpstreamModel":"deepseek-chat",
              "Stream":true,"Status":"ok","ErrorClass":"","PromptTokens":100,
              "CompletionTokens":50,"ReasoningTokens":0,"CachedTokens":0,"Estimated":false,
              "CostMicro":300000,"Currency":"CNY","LatencyFirstTokenMs":120,
              "LatencyTotalMs":900,"CreatedAt":"2026-09-12T00:00:00Z"}]}""";
    mvc.perform(
            post("/ingest/usage")
                .header("Authorization", "Bearer dev-ingest-token")
                .contentType("application/json")
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accepted").value(1));
    mvc.perform(get("/wallet/balance").header("Authorization", "Bearer " + adminToken))
        .andExpect(jsonPath("$.balanceMicros").value(11700000)); // 12_000_000 - 300_000
    // 同 ID 重投:幂等,余额不变
    mvc.perform(
            post("/ingest/usage")
                .header("Authorization", "Bearer dev-ingest-token")
                .contentType("application/json")
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replayed").value(1));
    mvc.perform(get("/wallet/balance").header("Authorization", "Bearer " + adminToken))
        .andExpect(jsonPath("$.balanceMicros").value(11700000));
  }

  @Test
  @Order(6)
  void pricing_ingress_matchesMuniuContract() throws Exception {
    mvc.perform(putPricing());
    // 下发契约:snake_case 字段 + 去尾零十进制字符串 + multiplier ≤4 位小数
    mvc.perform(get("/control/v1/pricing"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pricing[0].alias").value("deepseek-chat"))
        .andExpect(jsonPath("$.pricing[0].source").value("db"))
        .andExpect(jsonPath("$.pricing[0].input").value("0.001"))
        .andExpect(jsonPath("$.pricing[0].cached_input").value("0.0005"))
        .andExpect(jsonPath("$.pricing[0].output").value("0.002"))
        .andExpect(jsonPath("$.pricing[0].time_windows[0].multiplier").value("1.5"));
  }

  private static org.springframework.test.web.servlet.RequestBuilder putPricing() {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
            "/admin/pricing/deepseek-chat")
        .header("Authorization", "Bearer " + adminToken)
        .contentType("application/json")
        .content(
            body(
                """
                    {"currency":"CNY","inputMicros":1000,"cachedInputMicros":500,
                     "outputMicros":2000,"timezone":"Asia/Shanghai",
                     "timeWindows":[{"window":"peak","multiplier":"1.5"}]}"""));
  }
}
