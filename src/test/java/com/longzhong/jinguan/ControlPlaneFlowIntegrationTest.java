package com.longzhong.jinguan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.longzhong.jinguan.security.SigningKeys;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** 控制面主流程:租户/注册/权益/策略/目录/快照/审计,含认证负向与边界负向。 */
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-control-flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.service-auth.local-token=test-service-token",
      "jinguan.alerts.operational-token=ops-token",
      "jinguan.control.rate-limit.per-minute=100000",
      "jinguan.services.read.base-urls.kongming=http://127.0.0.1:1"
    })
// 断言集中在 ControlTestSupport.expect* 辅助方法;测试夹具使用固定回环/公网地址。
@SuppressWarnings({
  "PMD.UnitTestShouldIncludeAssert",
  "PMD.AvoidUsingHardCodedIP",
  "PMD.TooManyMethods",
  "PMD.AvoidDuplicateLiterals"
})
class ControlPlaneFlowIntegrationTest extends ControlTestSupport {

  @Autowired SigningKeys signingKeys;
  @Autowired JdbcTemplate jdbc;

  String adminToken;
  String userToken;
  String tenantId;
  String planId;
  String policyId;

  @BeforeAll
  void login() {
    adminToken = issue("control-admin", "ADMIN").access();
    userToken = issue("control-user", "USER").access();
  }

  @Test
  @Order(1)
  void tenantCrudRequiresAdminReasonAndAudits() throws Exception {
    MvcResult forbidden =
        adminPost(
            userToken, "/admin/tenants", "{\"tenantKey\":\"t1\",\"name\":\"一号\",\"reason\":\"r\"}");
    expectStatus(403, forbidden);

    MvcResult missingReason =
        adminPost(adminToken, "/admin/tenants", "{\"tenantKey\":\"t1\",\"name\":\"一号\"}");
    expectStatus(400, missingReason);

    MvcResult created =
        adminPost(
            adminToken,
            "/admin/tenants",
            "{\"tenantKey\":\"t1\",\"name\":\"一号租户\",\"reason\":\"开通\"}");
    expectStatus(200, created);
    expectJson(created, "/tenantKey", "t1");
    expectTrue(created.getResponse().getHeader("X-Audit-Id") != null, "写操作必须返回审计回执");
    tenantId = jsonString(created, "/id");

    MvcResult duplicate =
        adminPost(
            adminToken,
            "/admin/tenants",
            "{\"tenantKey\":\"t1\",\"name\":\"重复\",\"reason\":\"r\"}");
    expectStatus(409, duplicate);

    MvcResult member =
        adminPost(
            adminToken,
            "/admin/tenants/" + tenantId + "/members",
            "{\"subject\":\"sub-1\",\"role\":\"OWNER\",\"reason\":\"邀请\"}");
    expectStatus(200, member);
    expectJson(member, "/members/0/subject", "sub-1");

    MvcResult audit = adminGet(adminToken, "/admin/audit-events?action=TENANT_CREATE");
    expectStatus(200, audit);
    expectTrue(body(audit).size() >= 1, "租户创建必须留审计");
    expectNotContains(audit, "password");
  }

  @Test
  @Order(2)
  void disabledTenantRejectsNewMembersAndUnknownTenantIs404() throws Exception {
    MvcResult disabled =
        adminPost(adminToken, "/admin/tenants/" + tenantId + "/disable", "{\"reason\":\"停用\"}");
    expectStatus(200, disabled);
    expectJson(disabled, "/status", "DISABLED");

    MvcResult blockedMember =
        adminPost(
            adminToken,
            "/admin/tenants/" + tenantId + "/members",
            "{\"subject\":\"sub-2\",\"role\":\"MEMBER\",\"reason\":\"邀请\"}");
    expectStatus(409, blockedMember);

    MvcResult notFound = adminGet(adminToken, "/admin/tenants/does-not-exist");
    expectStatus(404, notFound);

    MvcResult enabled =
        adminPost(adminToken, "/admin/tenants/" + tenantId + "/enable", "{\"reason\":\"恢复\"}");
    expectStatus(200, enabled);
    expectJson(enabled, "/status", "ACTIVE");
  }

  @Test
  @Order(3)
  void registryRequiresServiceIdentityAndRejectsEndpointFields() throws Exception {
    String payload =
        """
        {"serviceId":"kongming","instanceId":"kongming-0","environment":"local","version":"1.0.0",
         "capabilities":["bot.basic","bot.store"],"adminCapabilities":["data_source_read"]}
        """;
    expectStatus(
        401,
        servicePostWithToken(
            "kongming", "/control/v1/registry/register", payload, null, "127.0.0.1"));
    expectStatus(
        401,
        servicePostWithToken(
            "kongming", "/control/v1/registry/register", payload, "wrong-token", "127.0.0.1"));
    expectStatus(
        403,
        servicePostWithToken(
            "kongming", "/control/v1/registry/register", payload, SERVICE_TOKEN, "10.1.2.3"));

    MvcResult registered = servicePost("kongming", "/control/v1/registry/register", payload);
    expectStatus(200, registered);
    expectJson(registered, "/serviceId", "kongming");
    expectJson(registered, "/health", "ACTIVE");
    expectTextContains(registered, "bot.basic");

    String withEndpoint =
        """
        {"serviceId":"kongming","instanceId":"kongming-1","environment":"local","version":"1.0.0",
         "capabilities":[],"endpoint":"http://leak"}
        """;
    expectStatus(400, servicePost("kongming", "/control/v1/registry/register", withEndpoint));

    String muniu =
        """
        {"serviceId":"muniu","instanceId":"muniu-0","environment":"local","version":"1.0.0",
         "capabilities":["model.chat"],"adminCapabilities":["provider_read"]}
        """;
    expectStatus(200, servicePost("muniu", "/control/v1/registry/register", muniu));

    MvcResult heartbeat =
        servicePost(
            "kongming",
            "/control/v1/registry/heartbeat",
            "{\"serviceId\":\"kongming\",\"instanceId\":\"kongming-0\"}");
    expectStatus(200, heartbeat);
    expectStatus(
        404,
        servicePost(
            "kongming",
            "/control/v1/registry/heartbeat",
            "{\"serviceId\":\"kongming\",\"instanceId\":\"ghost\"}"));
    expectStatus(
        403,
        servicePost(
            "muniu",
            "/control/v1/registry/heartbeat",
            "{\"serviceId\":\"kongming\",\"instanceId\":\"kongming-0\"}"));

    expectStatus(403, adminGet(userToken, "/admin/services"));
    MvcResult services = adminGet(adminToken, "/admin/services");
    expectStatus(200, services);
    expectTextContains(services, "bot.store");
    expectNotContains(services, "token");
  }

  @Test
  @Order(4)
  void planEntitlementsValidateRegistryCatalogAndBindings() throws Exception {
    String unknownKey =
        """
        {"planKey":"bad","name":"坏套餐","quotas":[],"entitlementKeys":["no.such.key"],"reason":"r"}
        """;
    expectStatus(400, adminPost(adminToken, "/admin/plans", unknownKey));

    String pro =
        """
        {"planKey":"pro","name":"专业版",
         "quotas":[{"dimension":"requests_per_day","unit":"count","limit":1000}],
         "entitlementKeys":["bot.basic"],"reason":"上架套餐"}
        """;
    MvcResult created = adminPost(adminToken, "/admin/plans", pro);
    expectStatus(200, created);
    planId = jsonString(created, "/id");
    expectJson(created, "/quotas/0/dimension", "requests_per_day");

    MvcResult bound =
        adminPost(
            adminToken,
            "/admin/tenants/" + tenantId + "/plan",
            "{\"planKey\":\"pro\",\"reason\":\"绑定\"}");
    expectStatus(200, bound);
    expectJson(bound, "/planKey", "pro");

    MvcResult blockedDelete =
        adminPost(adminToken, "/admin/plans/" + planId + "/delete", "{\"reason\":\"下架\"}");
    expectStatus(409, blockedDelete);

    MvcResult updated =
        adminPut(
            adminToken,
            "/admin/plans/" + planId,
            """
            {"name":"专业版 v2",
             "quotas":[{"dimension":"requests_per_day","unit":"count","limit":2000}],
             "entitlementKeys":["bot.basic","bot.store"],"reason":"调额"}
            """);
    expectStatus(200, updated);
    expectTrue(jsonInt(updated, "/version") >= 2, "套餐更新必须版本递增");
  }

  @Test
  @Order(5)
  void policyActivationRollbackAndRuleBodyRejection() throws Exception {
    String withRules =
        """
        {"packageKey":"shared-guard","serviceId":"kongming","scope":"GLOBAL","rulesetVersion":"v1",
         "reason":"r","rules":[{"type":"allow"}]}
        """;
    expectStatus(400, adminPost(adminToken, "/admin/policies", withRules));

    MvcResult created =
        adminPost(
            adminToken,
            "/admin/policies",
            """
            {"packageKey":"shared-guard","serviceId":"kongming","scope":"GLOBAL",
             "rulesetVersion":"v1",
             "reason":"初始化"}
            """);
    expectStatus(200, created);
    policyId = jsonString(created, "/id");
    expectJson(created, "/scope", "GLOBAL");

    expectStatus(
        400,
        adminPost(
            adminToken,
            "/admin/policies/" + policyId + "/activate",
            "{\"rulesetVersion\":\"v2\",\"reason\":\"r\"}"));
    MvcResult activated =
        adminPost(
            adminToken,
            "/admin/policies/" + policyId + "/activate",
            "{\"rulesetVersion\":\"v2\",\"reason\":\"升级\",\"confirm\":true}");
    expectStatus(200, activated);
    expectJson(activated, "/activeRulesetVersion", "v2");
    MvcResult rollback =
        adminPost(
            adminToken,
            "/admin/policies/" + policyId + "/rollback",
            "{\"reason\":\"回滚\",\"confirm\":true}");
    expectStatus(200, rollback);
    expectJson(rollback, "/activeRulesetVersion", "v1");
    expectNotContains(rollback, "rules");
  }

  @Test
  @Order(6)
  void catalogEntriesExposeAvailabilityAndNeverHideBrokenReferences() throws Exception {
    MvcResult created =
        adminPost(
            adminToken,
            "/admin/catalog",
            """
            {"entryKey":"assistant","botKey":"bot-1","title":"助手","visibility":"PUBLIC",
             "entitlementKey":"bot.basic","featured":true,"reason":"上架"}
            """);
    expectStatus(200, created);
    expectJson(created, "/availability", "UNKNOWN");

    jdbc.update(
        "INSERT INTO service_projections (service_id, etag, version, observed_at,"
            + " fetched_at, payload_json)"
            + " VALUES ('kongming','k1','1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,"
            + " '{\"bots\":[{\"bot_key\":\"bot-1\"}]}')");

    MvcResult available = adminGet(adminToken, "/admin/catalog");
    expectStatus(200, available);
    expectJson(available, "/0/availability", "AVAILABLE");

    MvcResult missing =
        adminPost(
            adminToken,
            "/admin/catalog",
            """
            {"entryKey":"ghost","botKey":"bot-404","title":"失联","visibility":"PUBLIC","reason":"上架"}
            """);
    expectStatus(200, missing);
    MvcResult listed = adminGet(adminToken, "/admin/catalog");
    expectJson(listed, "/1/availability", "MISSING");
    expectNotContains(listed, "password");
  }

  @Test
  @Order(7)
  void kongmingSnapshotIsSignedProjectedAndConditional() throws Exception {
    expectStatus(401, serviceGet(null, "/control/v1/snapshot", null));

    MvcResult snapshot = serviceGet("kongming", "/control/v1/snapshot", null);
    expectStatus(200, snapshot);
    expectJson(snapshot, "/service_id", "kongming");
    expectJson(snapshot, "/security_sections/0", "tenant_directory");
    expectTextContains(snapshot, "market_catalog");
    expectNotContains(snapshot, "\"pricing\"");

    final String bodyText = snapshot.getResponse().getContentAsString(StandardCharsets.UTF_8);
    String signature = snapshot.getResponse().getHeader("X-Jinguan-Signature");
    expectTrue(verifySignature(bodyText, signature), "快照签名必须可用 JWKS 公钥验签");
    expectTrue(
        signingKeys
            .active()
            .getKeyID()
            .equals(snapshot.getResponse().getHeader("X-Jinguan-Key-Id")),
        "签名 key id 必须与 active kid 一致");

    String etag = snapshot.getResponse().getHeader("ETag");
    MvcResult notModified = serviceGet("kongming", "/control/v1/snapshot", etag);
    expectStatus(304, notModified);
    expectTrue(
        notModified.getResponse().getContentAsString(StandardCharsets.UTF_8).isEmpty(),
        "304 不应返回响应体");
  }

  @Test
  @Order(8)
  void muniuSnapshotCarriesDataPlanePipesAndNoSensitiveValues() throws Exception {
    MvcResult snapshot = serviceGet("muniu", "/control/v1/snapshot", null);
    expectStatus(200, snapshot);
    expectJson(snapshot, "/service_id", "muniu");
    expectTextContains(snapshot, "\"pricing\"");
    expectTextContains(snapshot, "\"key_revocations\"");
    expectTextContains(snapshot, "\"topology\"");
    expectNotContains(snapshot, "market_catalog");

    final String bodyText = snapshot.getResponse().getContentAsString(StandardCharsets.UTF_8);
    expectNotContains(snapshot, "password");
    expectNotContains(snapshot, "secret");
    expectNotContains(snapshot, "connection_string");
    expectTrue(!bodyText.contains("\"dsn\""), "快照不得包含 DSN 字段");
  }

  @Test
  @Order(9)
  void tenantSnapshotVersionIsMonotonicAcrossWrites() throws Exception {
    long before = jsonInt(serviceGet("kongming", "/control/v1/snapshot", null), "/version");
    expectStatus(
        200,
        adminPatch(
            adminToken, "/admin/tenants/" + tenantId, "{\"name\":\"一号租户 v2\",\"reason\":\"改名\"}"));
    long after = jsonInt(serviceGet("kongming", "/control/v1/snapshot", null), "/version");
    expectTrue(after > before, "控制写操作必须递增快照版本: " + before + " -> " + after);
  }

  @Test
  @Order(10)
  void auditExportIsAdminOnlyAndAudited() throws Exception {
    expectStatus(403, adminGet(userToken, "/admin/audit-events/export.csv"));
    MvcResult exported = adminGet(adminToken, "/admin/audit-events/export.csv");
    expectStatus(200, exported);
    expectTextContains(exported, "occurred_at,actor");
    MvcResult audits = adminGet(adminToken, "/admin/audit-events?action=AUDIT_EXPORT");
    expectTrue(body(audits).size() >= 1, "导出必须留只读审计");
    expectNotContains(audits, "password");
  }

  private boolean verifySignature(String payload, String signature) throws Exception {
    Signature verifier = Signature.getInstance("SHA256withRSA");
    verifier.initVerify(signingKeys.active().toRSAPublicKey());
    verifier.update(payload.getBytes(StandardCharsets.UTF_8));
    return verifier.verify(Base64.getDecoder().decode(signature));
  }

  @Test
  @Order(11)
  void identityDirectoryIsReadOnlyAndSecretFree() throws Exception {
    MvcResult directory = adminGet(adminToken, "/admin/service-identities");
    expectStatus(200, directory);
    expectTextContains(directory, "kongming-control");
    expectNotContains(directory, "secret");
    expectNotContains(directory, "token");
    assertEquals(4, body(directory).size(), "目录应含四个方向性 client");
  }
}
