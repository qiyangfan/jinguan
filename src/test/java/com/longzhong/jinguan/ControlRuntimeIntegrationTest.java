package com.longzhong.jinguan;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import tools.jackson.databind.JsonNode;

/** 控制面运行时:命令队列、多来源用量、saga、审计保留;含认证/口径/边界负向。 */
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-control-runtime;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.service-auth.local-token=test-service-token",
      "jinguan.alerts.operational-token=ops-token",
      "jinguan.control.rate-limit.per-minute=100000",
      "jinguan.sagas.step-timeout-seconds=60"
    })
// 断言集中在 ControlTestSupport.expect* 辅助方法;测试覆盖多阶段状态机,刻意保持单类场景完整性。
@SuppressWarnings({
  "PMD.UnitTestShouldIncludeAssert",
  "PMD.TooManyMethods",
  "PMD.AvoidDuplicateLiterals",
  "PMD.AvoidUsingHardCodedIP"
})
class ControlRuntimeIntegrationTest extends ControlTestSupport {

  private static final int ONE = 1;

  @Autowired JdbcTemplate jdbc;
  @Autowired com.longzhong.jinguan.command.CommandMaintenanceService commandMaintenance;
  @Autowired com.longzhong.jinguan.saga.SagaService sagaService;
  @Autowired com.longzhong.jinguan.saga.SagaQueryService sagaQuery;
  @Autowired com.longzhong.jinguan.ingest.UsageEventService usageEventService;
  @Autowired com.longzhong.jinguan.audit.AuditRetentionService auditRetentionService;

  String adminToken;
  String userToken;
  String tenantId;
  String succeededCommandId;
  String pendingCommandId;

  @BeforeAll
  void seed() throws Exception {
    adminToken = issue("runtime-admin", "ADMIN").access();
    userToken = issue("runtime-user", "USER").access();
    expectStatus(
        200,
        servicePost(
            "kongming",
            "/control/v1/registry/register",
            """
            {"serviceId":"kongming","instanceId":"kongming-0","environment":"local",
             "version":"1.0.0",
             "capabilities":["bot.basic"],"adminCapabilities":[]}
            """));
    expectStatus(
        200,
        servicePost(
            "muniu",
            "/control/v1/registry/register",
            """
            {"serviceId":"muniu","instanceId":"muniu-0","environment":"local","version":"1.0.0",
             "capabilities":["model.chat"],"adminCapabilities":[]}
            """));
    MvcResult tenant =
        adminPost(
            adminToken,
            "/admin/tenants",
            "{\"tenantKey\":\"t1\",\"name\":\"一号\",\"reason\":\"开通\"}");
    expectStatus(200, tenant);
    tenantId = jsonString(tenant, "/id");
    MvcResult plan =
        adminPost(
            adminToken,
            "/admin/plans",
            """
            {"planKey":"pro","name":"专业版","quotas":[],"entitlementKeys":["bot.basic"],"reason":"上架"}
            """);
    expectStatus(200, plan);
  }

  @Test
  @Order(1)
  void commandSubmitValidatesActionAndIdempotency() throws Exception {
    expectStatus(
        403,
        adminPost(
            userToken,
            "/admin/commands",
            "{\"serviceId\":\"kongming\",\"action\":\"reindex\","
                + "\"actionSchemaVersion\":1,\"params\":{},\"reason\":\"r\"}"));
    expectStatus(
        400,
        adminPost(
            adminToken,
            "/admin/commands",
            "{\"serviceId\":\"kongming\",\"action\":\"drop_all\","
                + "\"actionSchemaVersion\":1,\"params\":{},\"reason\":\"r\"}"));
    expectStatus(
        400,
        adminPost(
            adminToken,
            "/admin/commands",
            "{\"serviceId\":\"kongming\",\"action\":\"reindex\",\"actionSchemaVersion\":1,"
                + "\"params\":{\"target\":\"everything\"},\"reason\":\"r\"}"));

    MvcResult submitted =
        adminPost(
            adminToken,
            "/admin/commands",
            """
            {"serviceId":"kongming","action":"reindex","actionSchemaVersion":1,
             "params":{"target":"usage"},"reason":"重建索引","idempotencyKey":"idem-1"}
            """);
    expectStatus(200, submitted);
    succeededCommandId = jsonString(submitted, "/id");
    expectJson(submitted, "/status", "PENDING");

    MvcResult replay =
        adminPost(
            adminToken,
            "/admin/commands",
            """
            {"serviceId":"kongming","action":"reindex","actionSchemaVersion":1,
             "params":{"target":"usage"},"reason":"重建索引","idempotencyKey":"idem-1"}
            """);
    expectStatus(200, replay);
    expectJson(replay, "/id", succeededCommandId);
    expectTrue(
        "true".equals(replay.getResponse().getHeader("X-Idempotent-Replay")), "重复幂等键必须标记 replay");
  }

  @Test
  @Order(2)
  void commandClaimAckRequiresServiceIdentity() throws Exception {
    expectStatus(
        401,
        servicePostWithToken("kongming", "/control/v1/commands/claim", "{}", null, "127.0.0.1"));
    MvcResult claimed = servicePost("kongming", "/control/v1/commands/claim", "{\"limit\":10}");
    expectStatus(200, claimed);
    expectTrue(body(claimed).size() == 1, "kongming 应只领取自己的命令");
    expectJson(claimed, "/0/id", succeededCommandId);
    expectJson(claimed, "/0/status", "CLAIMED");

    expectStatus(
        403,
        servicePost(
            "muniu",
            "/control/v1/commands/" + succeededCommandId + "/ack",
            "{\"status\":\"SUCCEEDED\"}"));
    MvcResult acked =
        servicePost(
            "kongming",
            "/control/v1/commands/" + succeededCommandId + "/ack",
            "{\"status\":\"SUCCEEDED\",\"result\":{\"reindexed\":3}}");
    expectStatus(200, acked);
    expectJson(acked, "/status", "SUCCEEDED");
    expectStatus(
        409,
        servicePost(
            "kongming",
            "/control/v1/commands/" + succeededCommandId + "/ack",
            "{\"status\":\"SUCCEEDED\"}"));
    MvcResult audits = adminGet(adminToken, "/admin/audit-events?action=COMMAND_ACK");
    expectTrue(body(audits).size() >= 1, "命令回执必须审计");
  }

  @Test
  @Order(3)
  void commandTimeoutRetriesThenDeadLettersAndPendingIsNeverPurged() throws Exception {
    MvcResult second =
        adminPost(
            adminToken,
            "/admin/commands",
            """
            {"serviceId":"kongming","action":"task_cancel","actionSchemaVersion":1,
             "params":{"task_id":"task-9"},"reason":"取消","idempotencyKey":"idem-2"}
            """);
    expectStatus(200, second);
    String commandId = jsonString(second, "/id");
    expectStatus(200, servicePost("kongming", "/control/v1/commands/claim", "{}"));
    ageClaimedAt(commandId);
    expectTrue(commandMaintenance.sweepTimeouts().get("retried") >= 1, "首次超时应回到待领取");
    expectJson(adminGet(adminToken, "/admin/commands/" + commandId), "/status", "PENDING");

    expectStatus(200, servicePost("kongming", "/control/v1/commands/claim", "{}"));
    ageClaimedAt(commandId);
    commandMaintenance.sweepTimeouts();
    expectStatus(200, servicePost("kongming", "/control/v1/commands/claim", "{}"));
    ageClaimedAt(commandId);
    expectTrue(commandMaintenance.sweepTimeouts().get("deadLettered") >= 1, "达到重试上限应进死信");
    expectJson(adminGet(adminToken, "/admin/commands/" + commandId), "/status", "DEAD_LETTER");

    MvcResult pending =
        adminPost(
            adminToken,
            "/admin/commands",
            """
            {"serviceId":"muniu","action":"cache_purge","actionSchemaVersion":1,
             "params":{"scope":"all"},"reason":"清缓存","idempotencyKey":"idem-3"}
            """);
    pendingCommandId = jsonString(pending, "/id");
    jdbc.update(
        "UPDATE control_commands SET finished_at = ? WHERE id = ?",
        Timestamp.from(Instant.now().minus(90, ChronoUnit.DAYS)),
        succeededCommandId);
    expectTrue(
        commandMaintenance.purgeFinished(Instant.now().minus(1, ChronoUnit.HOURS)) >= 1,
        "终态命令应被清理");
    expectStatus(200, adminGet(adminToken, "/admin/commands/" + pendingCommandId));
    expectStatus(200, adminGet(adminToken, "/admin/commands/" + commandId));
    MvcResult audits = adminGet(adminToken, "/admin/audit-events?action=COMMAND_DEAD_LETTER");
    expectTrue(body(audits).size() >= 1, "死信必须审计");
  }

  @Test
  @Order(4)
  void usageIngestEnforcesLlmOwnershipAndStaysIdempotent() throws Exception {
    String now = Instant.now().minusSeconds(60).toString();
    String event =
        "{\"events\":[{\"eventId\":\"evt-1\",\"tenantKey\":\"t1\",\"subject\":\"sub-1\","
            + "\"entitlementKey\":\"bot.basic\",\"metric\":\"bot.run\",\"quantity\":3,"
            + "\"unit\":\"count\",\"kind\":\"PRODUCT\",\"occurredAt\":\""
            + now
            + "\"}]}";
    MvcResult accepted = servicePost("kongming", "/ingest/events", event);
    expectStatus(200, accepted);
    expectJson(accepted, "/accepted", "1");
    expectJson(servicePost("kongming", "/ingest/events", event), "/replayed", "1");
    expectStatus(401, servicePostWithToken("kongming", "/ingest/events", event, null, "127.0.0.1"));

    String kongmingLlm =
        "{\"events\":[{\"eventId\":\"evt-llm\",\"metric\":\"llm.tokens\",\"quantity\":1,"
            + "\"unit\":\"count\",\"kind\":\"LLM\",\"occurredAt\":\""
            + now
            + "\"}]}";
    expectStatus(400, servicePost("kongming", "/ingest/events", kongmingLlm));

    String muniuLlm =
        "{\"events\":[{\"eventId\":\"evt-llm-2\",\"metric\":\"llm.tokens\",\"quantity\":9,"
            + "\"unit\":\"count\",\"kind\":\"LLM\",\"occurredAt\":\""
            + now
            + "\"}]}";
    MvcResult muniuAccepted = servicePost("muniu", "/ingest/events", muniuLlm);
    expectStatus(200, muniuAccepted);
    expectJson(muniuAccepted, "/accepted", "1");
    MvcResult audits = adminGet(adminToken, "/admin/audit-events?action=USAGE_REJECT");
    expectTrue(body(audits).size() >= 1, "用量拒绝必须审计");
  }

  @Test
  @Order(5)
  void healthEventsUpdateRegistryWithoutBillingRows() throws Exception {
    String healthUp =
        "{\"serviceId\":\"kongming\",\"instanceId\":\"kongming-0\",\"status\":\"UP\","
            + "\"observedAt\":\""
            + Instant.now()
            + "\"}";
    MvcResult health = servicePost("kongming", "/ingest/health", healthUp);
    expectStatus(200, health);
    expectJson(health, "/health", "ACTIVE");
    String mismatched =
        "{\"serviceId\":\"muniu\",\"instanceId\":\"muniu-0\",\"status\":\"UP\","
            + "\"observedAt\":\""
            + Instant.now()
            + "\"}";
    expectStatus(403, servicePost("kongming", "/ingest/health", mismatched));
    String withEndpoint =
        "{\"serviceId\":\"kongming\",\"instanceId\":\"kongming-0\",\"status\":\"UP\","
            + "\"observedAt\":\""
            + Instant.now()
            + "\",\"endpoint\":\"http://leak\"}";
    expectStatus(400, servicePost("kongming", "/ingest/health", withEndpoint));
    Integer billingRows =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM usage_events WHERE metric = 'bot.run' AND tenant_key = 't1'",
            Integer.class);
    expectTrue(billingRows != null && billingRows == ONE, "健康事件不得写入计费行");
  }

  @Test
  @Order(6)
  void usageAggregationKeepsUnaggregatedEvents() throws Exception {
    expectTrue(usageEventService.aggregatePending() >= 1, "首轮应聚合待处理事件");
    expectTrue(usageEventService.aggregatePending() == 0, "幂等:第二轮无事可做");
    String keepEvent =
        "{\"events\":[{\"eventId\":\"evt-keep\",\"tenantKey\":\"t1\","
            + "\"metric\":\"bot.keep\",\"quantity\":1,\"unit\":\"count\","
            + "\"kind\":\"PRODUCT\",\"occurredAt\":\""
            + Instant.now().minusSeconds(3600)
            + "\"}]}";
    expectStatus(200, servicePost("kongming", "/ingest/events", keepEvent));
    usageEventService.purgeAggregated(Instant.now().plus(1, ChronoUnit.DAYS));
    Integer kept =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM usage_events WHERE metric = 'bot.keep'", Integer.class);
    expectTrue(kept != null && kept == 1, "未聚合事件不得被清理");
    Integer aggregatedLeft =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM usage_events WHERE metric = 'bot.run'"
                + " AND aggregated_at IS NOT NULL",
            Integer.class);
    expectTrue(aggregatedLeft != null && aggregatedLeft == 0, "已聚合事件应被保留期清理");
    MvcResult aggregates =
        adminGet(adminToken, "/admin/usage-aggregates?source=kongming&tenant=t1&subject=sub-1");
    expectStatus(200, aggregates);
    expectTextContains(aggregates, "bot.run");
    expectStatus(403, adminGet(userToken, "/admin/usage-aggregates"));
  }

  @Test
  @Order(7)
  void sagaHappyPathRunsAdminAndServiceSteps() throws Exception {
    MvcResult started =
        adminPost(
            adminToken,
            "/admin/sagas",
            """
            {"type":"tenant_onboard","idempotencyKey":"saga-ok",
             "params":{"tenantKey":"t2","name":"二号","planKey":"pro"},
             "reason":"开通租户","confirm":true}
            """);
    expectStatus(200, started);
    final String sagaId = jsonString(started, "/id");
    expectJson(started, "/status", "RUNNING");
    expectJson(started, "/steps/0/status", "SUCCEEDED");
    expectJson(started, "/steps/2/status", "RUNNING");

    ackFirstSagaStep(sagaId);
    ackSecondSagaStep(sagaId);
    expectTrue("SUCCEEDED".equals(sagaQuery.view(sagaId).status()), "全部步骤成功后流程应为 succeeded");
    MvcResult detail = adminGet(adminToken, "/admin/sagas/" + sagaId);
    expectJson(detail, "/steps/3/status", "SUCCEEDED");
    expectJson(detail, "/status", "SUCCEEDED");
    MvcResult duplicate =
        adminPost(
            adminToken,
            "/admin/sagas",
            """
            {"type":"tenant_onboard","idempotencyKey":"saga-ok",
             "params":{"tenantKey":"t2","name":"二号","planKey":"pro"},
             "reason":"重复","confirm":true}
            """);
    expectJson(duplicate, "/id", sagaId);
  }

  @Test
  @Order(8)
  void sagaFailureCompensatesInReverseOrder() throws Exception {
    MvcResult started =
        adminPost(
            adminToken,
            "/admin/sagas",
            """
            {"type":"tenant_onboard","idempotencyKey":"saga-fail",
             "params":{"tenantKey":"t3","name":"三号","planKey":"pro"},
             "reason":"开通后失败","confirm":true}
            """);
    expectStatus(200, started);
    String sagaId = jsonString(started, "/id");
    claimAndAck("kongming", "SUCCEEDED", null);
    sagaService.advance(sagaId);
    claimAndAck("muniu", "FAILED", "模型回滚测试失败");
    sagaService.advance(sagaId);
    if (com.longzhong.jinguan.saga.SagaStatus.COMPENSATING.equals(
        sagaQuery.view(sagaId).status())) {
      claimAndAck("kongming", "SUCCEEDED", null);
      sagaService.advance(sagaId);
    }
    expectTrue("COMPENSATED".equals(sagaQuery.view(sagaId).status()), "服务失败后流程应补偿完成");
    MvcResult detail = adminGet(adminToken, "/admin/sagas/" + sagaId);
    expectJson(detail, "/steps/0/status", "COMPENSATED");
    expectJson(detail, "/steps/1/status", "COMPENSATED");
    String tenantStatus = jsonString(adminGet(adminToken, "/admin/tenants/" + tenantId), "/status");
    expectTrue("ACTIVE".equals(tenantStatus) || "DISABLED".equals(tenantStatus), "原租户不受影响");
    Integer disabled =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM tenants WHERE tenant_key = 't3' AND status = 'DISABLED'",
            Integer.class);
    expectTrue(disabled != null && disabled == 1, "补偿应停用 saga 创建的租户");
    expectTrue(
        body(adminGet(adminToken, "/admin/audit-events?action=SAGA_COMPENSATE")).size() >= 1,
        "补偿必须审计");
  }

  @Test
  @Order(9)
  void serviceOutageLeavesSagaPendingThenBlocksAndCloseRequiresReason() throws Exception {
    MvcResult started =
        adminPost(
            adminToken,
            "/admin/sagas",
            """
            {"type":"tenant_onboard","idempotencyKey":"saga-blocked",
             "params":{"tenantKey":"t4","name":"四号","planKey":"pro"},
             "reason":"停机演练","confirm":true}
            """);
    expectStatus(200, started);
    String sagaId = jsonString(started, "/id");
    expectJson(started, "/status", "RUNNING");
    jdbc.update(
        "UPDATE saga_steps SET started_at = ? WHERE saga_id = ? AND seq = 3",
        Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)),
        sagaId);
    sagaService.advance(sagaId);
    expectTrue("BLOCKED".equals(sagaQuery.view(sagaId).status()), "服务长时间无回执应进 blocked");
    expectStatus(400, adminPost(adminToken, "/admin/sagas/" + sagaId + "/close", "{}"));
    MvcResult closed =
        adminPost(adminToken, "/admin/sagas/" + sagaId + "/close", "{\"reason\":\"人工处理\"}");
    expectStatus(200, closed);
    expectTrue(jsonString(closed, "/closedAt") != null, "关闭必须记录 closedAt");
    Integer alerts =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM alert_events WHERE type = 'SAGA_BLOCKED'", Integer.class);
    expectTrue(alerts != null && alerts >= 1, "blocked 必须产生告警事件");
    expectTrue(
        body(adminGet(adminToken, "/admin/audit-events?action=SAGA_BLOCKED")).size() >= 1,
        "blocked 必须审计");
  }

  @Test
  @Order(10)
  void auditRetentionArchivesBeforePurge() throws Exception {
    String oldId = "00000000-0000-0000-0000-00000000dead";
    jdbc.update(
        "INSERT INTO control_audit_log (id, occurred_at, actor, actor_kind, action, result)"
            + " VALUES (?, ?, 'legacy', 'SYSTEM', 'TENANT_CREATE', 'SUCCESS')",
        oldId,
        Timestamp.from(Instant.now().minus(400, ChronoUnit.DAYS)));
    var result = auditRetentionService.archiveAndPurge(Instant.now().minus(180, ChronoUnit.DAYS));
    expectTrue(result.get("archived") >= 1, "到期审计应归档");
    Integer online =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM control_audit_log WHERE id = ?", Integer.class, oldId);
    expectTrue(online != null && online == 0, "在线表应清理到期行");
    Integer archived =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM control_audit_archive WHERE id = ?", Integer.class, oldId);
    expectTrue(archived != null && archived == 1, "归档表应保留历史");
    expectTrue(
        body(adminGet(adminToken, "/admin/audit-events?action=RETENTION_CLEANUP")).size() >= 1,
        "清理必须审计");
    Integer remain = jdbc.queryForObject("SELECT COUNT(*) FROM control_audit_log", Integer.class);
    expectTrue(remain != null && remain >= 1, "在线审计仍可查询");
  }

  private void ageClaimedAt(String commandId) {
    jdbc.update(
        "UPDATE control_commands SET claimed_at = ? WHERE id = ?",
        Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)),
        commandId);
  }

  private void ackFirstSagaStep(String sagaId) throws Exception {
    claimAndAck("kongming", "SUCCEEDED", null);
    sagaService.advance(sagaId);
  }

  private void ackSecondSagaStep(String sagaId) throws Exception {
    claimAndAck("muniu", "SUCCEEDED", null);
    sagaService.advance(sagaId);
  }

  private void claimAndAck(String serviceId, String status, String error) throws Exception {
    MvcResult claimed = servicePost(serviceId, "/control/v1/commands/claim", "{\"limit\":10}");
    expectStatus(200, claimed);
    String commandId = null;
    for (JsonNode command : body(claimed)) {
      String action = command.path("action").asString();
      if (action.startsWith("tenant_") || action.startsWith("plan_")) {
        commandId = command.path("id").asString();
        break;
      }
    }
    expectTrue(commandId != null, "应领取到 saga 命令: " + claimed.getResponse().getContentAsString());
    String ackBody =
        "FAILED".equals(status)
            ? "{\"status\":\"FAILED\",\"error\":\"" + error + "\"}"
            : "{\"status\":\"SUCCEEDED\",\"result\":{\"ok\":true}}";
    expectStatus(
        200, servicePost(serviceId, "/control/v1/commands/" + commandId + "/ack", ackBody));
  }
}
