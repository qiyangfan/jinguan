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
  static String userBToken;

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
                            {"userId":1,"amountCredits":10000000000,"refId":"seed-1","note":"种子资金"}""")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.balanceCredits").value("10000000000"));
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
                            {"userId":1,"amountCredits":10000000000,"refId":"seed-1"}""")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.balanceCredits").value("10000000000")); // 重放不加钱
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
                            {"provider":"mock","amountCredits":2000000000,"currency":"CNY"}""")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payUrl").exists())
            .andReturn();
    String orderId =
        com.jayway.jsonpath.JsonPath.read(order.getResponse().getContentAsString(), "$.orderId");

    mvc.perform(post("/payments/mock/{id}/pay", orderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replayed").value(false));
    mvc.perform(get("/wallet/balance").header("Authorization", "Bearer " + adminToken))
        .andExpect(jsonPath("$.balanceCredits").value("12000000000"));
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
              "CostCredits":300000000,"Currency":"CNY","LatencyFirstTokenMs":120,
              "LatencyTotalMs":900,"CreatedAt":"2026-09-12T00:00:00Z"}]}""";
    mvc.perform(
            post("/ingest/usage")
                .header("Authorization", "Bearer dev-ingest-token")
                .contentType("application/json")
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accepted").value(1));
    mvc.perform(get("/wallet/balance").header("Authorization", "Bearer " + adminToken))
        .andExpect(
            jsonPath("$.balanceCredits").value("11700000000")); // 12_000_000_000 - 300_000_000
    // 同 ID 重投:幂等,余额不变
    mvc.perform(
            post("/ingest/usage")
                .header("Authorization", "Bearer dev-ingest-token")
                .contentType("application/json")
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replayed").value(1));
    mvc.perform(get("/wallet/balance").header("Authorization", "Bearer " + adminToken))
        .andExpect(jsonPath("$.balanceCredits").value("11700000000"));
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
                    {"currency":"CNY","inputCredits":1000000,"cachedInputCredits":500000,
                     "outputCredits":2000000,"timezone":"Asia/Shanghai",
                     "timeWindows":[{"window":"peak","multiplier":"1.5"}]}"""));
  }

  @Test
  @Order(7)
  void orderOwnership_enforced_listServerSide() throws Exception {
    // admin(uid=1)下单
    MvcResult order =
        mvc.perform(
                post("/payments/orders")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json")
                    .content(
                        body(
                            """
                            {"provider":"mock","amountCredits":1000000000,"currency":"CNY"}""")))
            .andExpect(status().isOk())
            .andReturn();
    String orderId =
        com.jayway.jsonpath.JsonPath.read(order.getResponse().getContentAsString(), "$.orderId");
    // 第二用户(USER):越权查询他人订单 → 404 不泄露存在性
    MvcResult regB =
        mvc.perform(
                post("/auth/register")
                    .contentType("application/json")
                    .content(
                        body(
                            """
                            {"username":"order-b","password":"password-123"}""")))
            .andExpect(status().isCreated())
            .andReturn();
    userBToken =
        com.jayway.jsonpath.JsonPath.read(regB.getResponse().getContentAsString(), "$.token");
    mvc.perform(
            get("/payments/orders/{id}", orderId).header("Authorization", "Bearer " + userBToken))
        .andExpect(status().isNotFound());
    // 本人查询 → 200
    mvc.perform(
            get("/payments/orders/{id}", orderId).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CREATED"));
    // 订单簿服务端事实源:admin 列表含该单(新→旧,首位),userB 列表为空
    mvc.perform(get("/payments/orders").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].orderId").value(orderId));
    mvc.perform(get("/payments/orders").header("Authorization", "Bearer " + userBToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  @Order(8)
  void adminDebit_adjusts_audits_operator() throws Exception {
    // 余额 11,700,000(Order 5 终态);调账扣减 700,000
    mvc.perform(
            post("/wallet/admin/debit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(
                    body(
                        """
                            {"userId":1,"amountCredits":700000000,"refId":"adjust-1","note":"错充修正"}""")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.balanceCredits").value("11000000000"));
    // 同 refId 重放:幂等不双扣
    mvc.perform(
            post("/wallet/admin/debit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(
                    body(
                        """
                            {"userId":1,"amountCredits":700000000,"refId":"adjust-1"}""")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.balanceCredits").value("11000000000"));
    // 余额不足调账:422 且零痕迹
    mvc.perform(
            post("/wallet/admin/debit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(
                    body(
                        """
                            {"userId":1,"amountCredits":99999999999,"refId":"adjust-x"}""")))
        .andExpect(status().isUnprocessableEntity());
    // 操作人审计:MANUAL_ADJUST 分录含 operatorUserId=1;USER 调用 → 403
    mvc.perform(get("/wallet/entries").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].reason").value("MANUAL_ADJUST"))
        .andExpect(jsonPath("$[0].operatorUserId").value("1"))
        .andExpect(jsonPath("$[0].amountCredits").value("-700000000"));
    mvc.perform(
            post("/wallet/admin/debit")
                .header("Authorization", "Bearer " + userBToken)
                .contentType("application/json")
                .content(
                    body(
                        """
                            {"userId":1,"amountCredits":1}""")))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(9)
  void ingest_wrongToken_rejected() throws Exception {
    mvc.perform(
            post("/ingest/usage")
                .header("Authorization", "Bearer wrong-token")
                .contentType("application/json")
                .content(body("{\"rows\":[]}")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @Order(10)
  void adminResetPassword_oldPasswordInvalidates() throws Exception {
    // ADMIN 重置 userB(uid=2)密码;USER 调用 → 403
    mvc.perform(
            post("/admin/users/2/reset-password")
                .header("Authorization", "Bearer " + userBToken)
                .contentType("application/json")
                .content(body("{\"newPassword\":\"new-password-123\"}")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/admin/users/2/reset-password")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(body("{\"newPassword\":\"new-password-123\"}")))
        .andExpect(status().isNoContent());
    // 旧密码失效:统一话术 400(不泄露用户名存在性)
    mvc.perform(
            post("/auth/login")
                .contentType("application/json")
                .content(body("{\"username\":\"order-b\",\"password\":\"password-123\"}")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("用户名或密码错误"));
    // 新密码登录成功
    mvc.perform(
            post("/auth/login")
                .contentType("application/json")
                .content(body("{\"username\":\"order-b\",\"password\":\"new-password-123\"}")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").exists());
  }

  @Test
  @Order(11)
  void usageRecords_persisted_andQueryable() throws Exception {
    // Order 5 的 ingest 行(u-1,首投 accepted + 重投 replayed):明细留存,投影最终 accepted,attempts=2
    mvc.perform(get("/wallet/usage").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].usageId").value("u-1"))
        .andExpect(jsonPath("$[0].finalOutcome").value("accepted"))
        .andExpect(jsonPath("$[0].attempts").value(2))
        .andExpect(jsonPath("$[0].costCredits").value("300000000"))
        .andExpect(jsonPath("$[0].modelAlias").value("deepseek-chat"));
    // 管理面按用户查询:uid=2(order-b)无用量 → 空
    mvc.perform(get("/admin/usage?userId=2").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }
}
