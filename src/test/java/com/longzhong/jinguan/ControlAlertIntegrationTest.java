package com.longzhong.jinguan;

import com.longzhong.jinguan.alerts.AlertDeliveryScheduler;
import com.longzhong.jinguan.alerts.AlertService;
import com.longzhong.jinguan.alerts.AlertTypes;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** 告警:运维上报 token、dedup、webhook 签名投递、重试与确认;载荷脱敏。 */
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-control-alert;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.service-auth.local-token=test-service-token",
      "jinguan.alerts.operational-token=ops-token",
      "jinguan.alerts.webhook-secret=test-secret",
      "jinguan.alerts.dedup-window-seconds=900",
      "jinguan.alerts.max-attempts=3",
      "jinguan.alerts.delivery-interval-seconds=3600",
      "jinguan.alerts.delivery-initial-delay-seconds=3600",
      "jinguan.control.rate-limit.per-minute=100000"
    })
// 断言集中在 ControlTestSupport.expect*;webhook/密钥为测试夹具固定值。
@SuppressWarnings({
  "PMD.UnitTestShouldIncludeAssert",
  "PMD.AvoidUsingHardCodedIP",
  "PMD.AvoidDuplicateLiterals"
})
class ControlAlertIntegrationTest extends ControlTestSupport {

  private static final HttpServer SERVER;
  private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();
  private static final AtomicInteger FAILURES = new AtomicInteger();

  static {
    try {
      SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      SERVER.createContext(
          "/hook",
          exchange -> {
            String body =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            RECEIVED.add(
                exchange.getRequestHeaders().getFirst("X-Jinguan-Timestamp")
                    + "|"
                    + exchange.getRequestHeaders().getFirst("X-Jinguan-Signature")
                    + "|"
                    + body);
            if (FAILURES.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
              exchange.sendResponseHeaders(500, -1);
            } else {
              exchange.sendResponseHeaders(200, -1);
            }
            exchange.close();
          });
      SERVER.start();
    } catch (IOException e) {
      throw new IllegalStateException("无法启动 webhook 测试服务", e);
    }
  }

  @DynamicPropertySource
  static void alertProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "jinguan.alerts.webhook-url",
        () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/hook");
    registry.add("jinguan.alerts.webhook-allowlist", () -> "127.0.0.1");
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired AlertService alertService;
  @Autowired AlertDeliveryScheduler deliveryScheduler;
  @Autowired com.longzhong.jinguan.alerts.AlertRetentionService alertRetentionService;

  String adminToken;

  @BeforeAll
  void login() {
    adminToken = issue("alert-admin", "ADMIN").access();
  }

  @Test
  @Order(1)
  void operationalAlertsRequireTokenAndRedactPayloads() throws Exception {
    String payload =
        """
        {"type":"BACKUP_FAILURE","severity":"CRITICAL",
         "summary":{"dsn":"postgres://user:pass@db/x","password":"hunter2"},
         "runbook":"docs/control-ha.md"}
        """;
    expectStatus(
        401,
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/control/v1/alerts/operational")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn());
    MvcResult accepted =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/control/v1/alerts/operational")
                    .header("X-Operational-Token", OPS_TOKEN)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();
    expectStatus(202, accepted);
    String stored =
        jdbc.queryForObject(
            "SELECT payload_json FROM alert_events WHERE type = 'BACKUP_FAILURE'", String.class);
    expectTrue(stored != null && !stored.contains("hunter2"), "告警载荷不得含凭据原值");
    expectTrue(stored != null && stored.contains("***"), "敏感键应替换为脱敏占位");
    expectStatus(
        400,
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/control/v1/alerts/operational")
                    .header("X-Operational-Token", OPS_TOKEN)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"type\":\"UNKNOWN_TYPE\"}"))
            .andReturn());
  }

  @Test
  @Order(2)
  void deduplicatedAlertsDoNotFlood() {
    Map<String, Object> payload = Map.of("detail", "db down");
    boolean first =
        alertService.raise(
            AlertTypes.DB_FAILURE,
            "CRITICAL",
            "test",
            null,
            null,
            "dedup-1",
            payload,
            "docs/control-ha.md");
    boolean second =
        alertService.raise(
            AlertTypes.DB_FAILURE,
            "CRITICAL",
            "test",
            null,
            null,
            "dedup-1",
            payload,
            "docs/control-ha.md");
    expectTrue(first, "首次告警应入队");
    expectTrue(!second, "同 dedup_key 在窗口内应被合并");
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM alert_events WHERE dedup_key = 'dedup-1'", Integer.class);
    expectTrue(count != null && count == 1, "去重窗口内只保留一条");
  }

  @Test
  @Order(3)
  void webhookDeliverySignsAndRetries() throws Exception {
    alertService.raise(
        AlertTypes.CONTRACT_DRIFT,
        "WARNING",
        "test",
        null,
        "kongming",
        "dedup-retry",
        Map.of("detail", "schema drift"),
        "docs/control-ha.md");
    String id =
        jdbc.queryForObject(
            "SELECT id FROM alert_events WHERE dedup_key = 'dedup-retry'", String.class);
    FAILURES.set(1);
    deliveryScheduler.deliverOne(id);
    expectTrue("PENDING".equals(statusOf(id)), "通道失败应保持待投递");
    deliveryScheduler.deliverOne(id);
    expectTrue("DELIVERED".equals(statusOf(id)), "重试成功应标记已投递");

    String received =
        RECEIVED.stream()
            .filter(entry -> entry.contains("CONTRACT_DRIFT"))
            .findFirst()
            .orElse(null);
    expectTrue(received != null, "webhook 应收到告警");
    String[] parts = received.split("\\|", 3);
    expectTrue(parts[1].startsWith("sha256="), "webhook 必须携带签名");
    expectTrue(!parts[0].isBlank(), "webhook 必须携带时间戳");
    expectTrue(parts[2].contains("docs/control-ha.md"), "webhook 载荷应含 runbook");
    expectTrue(!parts[2].contains("secret"), "webhook 载荷不得含敏感值");
    expectTrue(
        hmacOf(parts[0] + "." + parts[2]).equals(parts[1].substring("sha256=".length())),
        "签名必须覆盖时间戳与载荷");
  }

  @Test
  @Order(4)
  void alertsAreListedAckedAndAudited() throws Exception {
    MvcResult list = adminGet(adminToken, "/admin/alerts");
    expectStatus(200, list);
    expectTextContains(list, "BACKUP_FAILURE");
    expectNotContains(list, "hunter2");
    String id =
        jdbc.queryForObject(
            "SELECT id FROM alert_events WHERE type = 'BACKUP_FAILURE'", String.class);
    expectStatus(
        204,
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/admin/alerts/" + id + "/ack")
                    .header("Authorization", "Bearer " + adminToken))
            .andReturn());
    expectTrue("ACKED".equals(statusOf(id)), "人工确认后状态应为 ACKED");
    expectTrue(
        body(adminGet(adminToken, "/admin/audit-events?action=ALERT_ACK")).size() >= 1, "确认必须审计");
  }

  private String statusOf(String id) {
    return jdbc.queryForObject("SELECT status FROM alert_events WHERE id = ?", String.class, id);
  }

  @SuppressWarnings("PMD.HardCodedCryptoKey") // 测试夹具固定共享密钥,非生产凭据
  private static String hmacOf(String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return java.util.HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  @Order(5)
  void terminalAlertsArePurgedWithAudit() throws Exception {
    jdbc.update(
        "UPDATE alert_events SET created_at = ? WHERE type = 'BACKUP_FAILURE'",
        java.sql.Timestamp.from(Instant.now().minus(200, java.time.temporal.ChronoUnit.DAYS)));
    int purged =
        alertRetentionService.purgeTerminal(
            Instant.now().minus(90, java.time.temporal.ChronoUnit.DAYS));
    expectTrue(purged >= 1, "超过保留期的终态告警应被清理");
    Integer remain =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM alert_events WHERE type = 'BACKUP_FAILURE'", Integer.class);
    expectTrue(remain != null && remain == 0, "清理后不应残留");
    expectTrue(
        body(adminGet(adminToken, "/admin/audit-events?action=ALERT_PURGE")).size() >= 1,
        "告警清理必须审计");
  }
}
