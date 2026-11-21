package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 端到端验证注册、入账、支付、绑定和用量扣费流程；测试方法共享状态并按序执行。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "jinguan.security.jwt-secret=test-secret-0123456789abcdef-0123456789abcdef",
    })
class DemoLoopIntegrationTest extends RefreshTestSupport {

  private static final String AUTHORIZATION = "Authorization";
  private static final String BEARER = "Bearer ";
  private static final String BALANCE_PATH = "$.balanceCredits";

  @Autowired MockMvc mvc;

  String adminToken;
  String adminSubject = "sub-solvela";

  @Test
  @Order(1)
  void registerFirstUserBecomesAdmin() {
    var principal =
        principals.save(com.longzhong.jinguan.user.PrincipalEntity.jit(adminSubject, "solvela"));
    principal.grantRole("ADMIN");
    adminToken = tokenIssuer.issue(principals.save(principal)).access();
    Assertions.assertNotNull(adminToken, "管理员必须取得 token");
  }

  @Test
  @Order(2)
  void manualCreditGivesBalance() throws Exception {
    MvcResult credited =
        mvc.perform(
                post("/wallet/admin/credit")
                    .header(AUTHORIZATION, BEARER + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"subject":"sub-solvela","amountCredits":10000000000,
                         "refId":"seed-1","note":"种子资金"}"""))
            .andExpect(jsonPath(BALANCE_PATH).value("10000000000"))
            .andReturn();
    Assertions.assertEquals(201, credited.getResponse().getStatus(), "入账应返回 201");
  }

  @Test
  @Order(3)
  void manualCreditSameRefIdReplaysWithoutDoubleCredit() throws Exception {
    MvcResult replay =
        mvc.perform(
                post("/wallet/admin/credit")
                    .header(AUTHORIZATION, BEARER + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"subject":"sub-solvela","amountCredits":10000000000,"refId":"seed-1"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath(BALANCE_PATH).value("10000000000")) // 重放不加钱
            .andReturn();
    Assertions.assertEquals(201, replay.getResponse().getStatus(), "同键重放应返回 201");
  }

  @Test
  @Order(4)
  void mockPaymentLoopEntersLedger() throws Exception {
    // 支付成功后入账 2,000,000,000 credits。
    MvcResult order =
        mvc.perform(
                post("/payments/orders")
                    .header(AUTHORIZATION, BEARER + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"provider":"mock","amountCredits":2000000000,"currency":"CNY"}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payUrl").exists())
            .andReturn();
    String orderId = JsonPath.read(order.getResponse().getContentAsString(), "$.orderId");

    mvc.perform(post("/payments/mock/{id}/pay", orderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replayed").value(false));
    MvcResult balance =
        mvc.perform(get("/wallet/balance").header(AUTHORIZATION, BEARER + adminToken))
            .andExpect(jsonPath(BALANCE_PATH).value("12000000000"))
            .andReturn();
    Assertions.assertEquals(200, balance.getResponse().getStatus(), "余额查询应 200");
  }

  @Test
  @Order(5)
  void ingestUsageRowDebitsByKeyBinding() throws Exception {
    mvc.perform(
            post("/admin/key-bindings")
                .header(AUTHORIZATION, BEARER + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"keyId":"mk-test-1","subject":"sub-solvela"}"""))
        .andExpect(status().isCreated());
    // muniu UsageRow 使用 PascalCase 字段名。
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
                .header(AUTHORIZATION, BEARER + "dev-ingest-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accepted").value(1));
    mvc.perform(get("/wallet/balance").header(AUTHORIZATION, BEARER + adminToken))
        .andExpect(jsonPath(BALANCE_PATH).value("11700000000")); // 12_000_000_000 - 300_000_000
    // 同 ID 重投不重复扣款。
    MvcResult replay =
        mvc.perform(
                post("/ingest/usage")
                    .header(AUTHORIZATION, BEARER + "dev-ingest-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.replayed").value(1))
            .andReturn();
    Assertions.assertEquals(200, replay.getResponse().getStatus(), "重投应 200");
    mvc.perform(get("/wallet/balance").header(AUTHORIZATION, BEARER + adminToken))
        .andExpect(jsonPath(BALANCE_PATH).value("11700000000"));
  }

  @Test
  @Order(6)
  void pricingIngressMatchesMuniuContract() throws Exception {
    mvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/admin/pricing/deepseek-chat")
            .header(AUTHORIZATION, BEARER + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {"currency":"CNY","inputCredits":1000000,"cachedInputCredits":500000,
                     "outputCredits":2000000,"timezone":"Asia/Shanghai",
                     "timeWindows":[{"window":"peak","multiplier":"1.5"}]}"""));
    // 下发字段、价格格式和倍率精度符合契约。
    MvcResult pricing =
        mvc.perform(get("/control/v1/pricing"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pricing[0].alias").value("deepseek-chat"))
            .andExpect(jsonPath("$.pricing[0].source").value("db"))
            .andExpect(jsonPath("$.pricing[0].input").value("0.001"))
            .andExpect(jsonPath("$.pricing[0].cached_input").value("0.0005"))
            .andExpect(jsonPath("$.pricing[0].output").value("0.002"))
            .andExpect(jsonPath("$.pricing[0].time_windows[0].multiplier").value("1.5"))
            .andReturn();
    Assertions.assertEquals(200, pricing.getResponse().getStatus(), "价目下发应 200");
  }
}
