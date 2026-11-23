package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
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

/** 管理端流程:订单归属、调账审计、改密与用量查询;前置状态在 {@link #seed()} 中一次建成。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-admin-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "jinguan.security.jwt-secret=test-secret-0123456789abcdef-0123456789abcdef",
    })
class DemoAdminIntegrationTest extends RefreshTestSupport {

  private static final String AUTHORIZATION = "Authorization";
  private static final String BEARER = "Bearer ";
  private static final String ORDERS_PATH = "/payments/orders";
  private static final String DEBIT_PATH = "/wallet/admin/debit";

  @Autowired MockMvc mvc;

  String adminToken;
  String secondaryUserToken;

  private String loginAs(String username) {
    String subject = "sub-" + username;
    var principal =
        principals
            .findById(subject)
            .orElseGet(
                () ->
                    principals.save(
                        com.longzhong.jinguan.user.PrincipalEntity.jit(subject, username)));
    if (username.contains("solvela")) {
      principal.syncRole("ADMIN");
      principal = principals.save(principal);
    }
    return tokenIssuer.issue(principal).access();
  }

  /** 复现管理端用例依赖的资金与用量前置:余额 11,700,000,000,用户 2 可用。 */
  @BeforeAll
  void seed() throws Exception {
    adminToken = loginAs("solvela");
    secondaryUserToken = loginAs("order-b");
    mvc.perform(
            post("/wallet/admin/credit")
                .header(AUTHORIZATION, BEARER + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"subject":"sub-solvela","amountCredits":10000000000,
                     "refId":"seed-1","note":"种子资金"}"""))
        .andExpect(status().isCreated());
    MvcResult order =
        mvc.perform(
                post(ORDERS_PATH)
                    .header(AUTHORIZATION, BEARER + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"provider":"mock","amountCredits":2000000000,"currency":"CNY"}"""))
            .andExpect(status().isOk())
            .andReturn();
    String orderId = JsonPath.read(order.getResponse().getContentAsString(), "$.orderId");
    mvc.perform(post("/payments/mock/{id}/pay", orderId)).andExpect(status().isOk());
    mvc.perform(
            post("/admin/key-bindings")
                .header(AUTHORIZATION, BEARER + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"keyId":"mk-test-1","subject":"sub-solvela"}"""))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/ingest/usage")
                .header(AUTHORIZATION, BEARER + "dev-ingest-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"rows":[{"ID":"u-1","RequestID":"r-1","TenantID":"t1","APIKeyID":"mk-test-1",
                      "Provider":"deepseek","ModelAlias":"deepseek-chat",
                      "UpstreamModel":"deepseek-chat","Stream":true,"Status":"ok","ErrorClass":"",
                      "PromptTokens":100,"CompletionTokens":50,"ReasoningTokens":0,
                      "CachedTokens":0,"Estimated":false,"CostCredits":300000000,"Currency":"CNY",
                      "LatencyFirstTokenMs":120,"LatencyTotalMs":900,
                      "CreatedAt":"2026-09-12T00:00:00Z"}]}"""))
        .andExpect(status().isOk());
  }

  @Test
  @Order(1)
  void orderOwnershipEnforcedListServerSide() throws Exception {
    // 管理员下单。
    MvcResult order =
        mvc.perform(
                post(ORDERS_PATH)
                    .header(AUTHORIZATION, BEARER + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"provider":"mock","amountCredits":1000000000,"currency":"CNY"}"""))
            .andExpect(status().isOk())
            .andReturn();
    String orderId = JsonPath.read(order.getResponse().getContentAsString(), "$.orderId");
    // 他人订单返回 404，不泄露其存在性。
    MvcResult foreign =
        mvc.perform(
                get("/payments/orders/{id}", orderId)
                    .header(AUTHORIZATION, BEARER + secondaryUserToken))
            .andExpect(status().isNotFound())
            .andReturn();
    Assertions.assertEquals(404, foreign.getResponse().getStatus(), "他人订单必须 404");
    // 本人可查询订单。
    mvc.perform(get("/payments/orders/{id}", orderId).header(AUTHORIZATION, BEARER + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CREATED"));
    // 管理员列表含该单，普通用户列表为空。
    mvc.perform(get(ORDERS_PATH).header(AUTHORIZATION, BEARER + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].orderId").value(orderId));
    MvcResult listB =
        mvc.perform(get(ORDERS_PATH).header(AUTHORIZATION, BEARER + secondaryUserToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0))
            .andReturn();
    Assertions.assertEquals(200, listB.getResponse().getStatus(), "普通用户列表查询应 200");
  }

  @Test
  @Order(2)
  void adminDebitAdjustsAuditsOperator() throws Exception {
    // 调账扣减 700,000,000 credits。
    mvc.perform(
            post(DEBIT_PATH)
                .header(AUTHORIZATION, BEARER + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"subject":"sub-solvela","amountCredits":700000000,
                     "refId":"adjust-1","note":"错充修正"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.balanceCredits").value("11000000000"));
    // 同 refId 重放不重复扣款。
    mvc.perform(
            post(DEBIT_PATH)
                .header(AUTHORIZATION, BEARER + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"subject":"sub-solvela","amountCredits":700000000,"refId":"adjust-1"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.balanceCredits").value("11000000000"));
    // 余额不足时拒绝调账且不留分录。
    MvcResult insufficient =
        mvc.perform(
                post(DEBIT_PATH)
                    .header(AUTHORIZATION, BEARER + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"subject":"sub-solvela","amountCredits":99999999999,
                         "refId":"adjust-x"}"""))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    Assertions.assertEquals(422, insufficient.getResponse().getStatus(), "余额不足必须 422");
    // 调账分录记录管理员；普通用户调用返回 403。
    mvc.perform(get("/wallet/entries").header(AUTHORIZATION, BEARER + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].reason").value("MANUAL_ADJUST"))
        .andExpect(jsonPath("$[0].operatorSubject").value("sub-solvela"))
        .andExpect(jsonPath("$[0].amountCredits").value("-700000000"));
    MvcResult forbidden =
        mvc.perform(
                post(DEBIT_PATH)
                    .header(AUTHORIZATION, BEARER + secondaryUserToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"subject":"sub-solvela","amountCredits":1}"""))
            .andExpect(status().isForbidden())
            .andReturn();
    Assertions.assertEquals(403, forbidden.getResponse().getStatus(), "普通用户调账应 403");
  }

  @Test
  @Order(3)
  void ingestWrongTokenRejected() throws Exception {
    MvcResult rejected =
        mvc.perform(
                post("/ingest/usage")
                    .header(AUTHORIZATION, BEARER + "wrong-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rows\":[]}"))
            .andExpect(status().isUnauthorized())
            .andReturn();
    Assertions.assertEquals(401, rejected.getResponse().getStatus(), "错误 ingest token 必须 401");
  }

  @Test
  @Order(4)
  void roleEndpointRetiredWhileDisableStillWorks() throws Exception {
    // 角色改由 Keycloak client roles 管理,本地角色端点必须下线。
    mvc.perform(
            post("/admin/users/sub-order-b/role")
                .header(AUTHORIZATION, BEARER + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
        .andExpect(status().is4xxClientError());
    Assertions.assertEquals(
        "USER", principals.findById("sub-order-b").orElseThrow().getRole(), "本地不得再授予角色");
    mvc.perform(post("/admin/users/sub-order-b/disable").header(AUTHORIZATION, BEARER + adminToken))
        .andExpect(status().isNoContent());
    Assertions.assertTrue(principals.findById("sub-order-b").orElseThrow().isDisabled(), "禁用必须落库");
  }

  @Test
  @Order(5)
  void usageRecordsPersistedAndQueryable() throws Exception {
    // 用量明细保留投递记录，最终状态为 accepted。
    MvcResult usage =
        mvc.perform(get("/wallet/usage").header(AUTHORIZATION, BEARER + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].usageId").value("u-1"))
            .andExpect(jsonPath("$[0].finalOutcome").value("accepted"))
            .andExpect(jsonPath("$[0].attempts").value(1))
            .andExpect(jsonPath("$[0].costCredits").value("300000000"))
            .andExpect(jsonPath("$[0].modelAlias").value("deepseek-chat"))
            .andReturn();
    Assertions.assertEquals(200, usage.getResponse().getStatus(), "用量明细查询应 200");
    // 无用量用户的管理查询返回空列表。
    mvc.perform(get("/admin/usage?subject=sub-order-b").header(AUTHORIZATION, BEARER + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
