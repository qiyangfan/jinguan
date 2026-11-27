package com.longzhong.jinguan;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

/** 服务只读投影:经服务只读 API 拉取、脱敏缓存、stale/unknown 语义、敏感读审计。 */
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-control-projection;MODE=PostgreSQL;"
          + "DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.service-auth.local-token=test-service-token",
      "jinguan.control.rate-limit.per-minute=100000",
      "jinguan.services.read.local-token=read-token",
      "jinguan.services.read.ttl-seconds=1",
      "jinguan.services.read.stale-after-seconds=1",
      "jinguan.services.read.retention-days=1"
    })
// 断言集中在 ControlTestSupport.expect*;测试服务固定回环地址。
@SuppressWarnings({
  "PMD.UnitTestShouldIncludeAssert",
  "PMD.AvoidUsingHardCodedIP",
  "PMD.AvoidDuplicateLiterals"
})
class ServiceProjectionIntegrationTest extends ControlTestSupport {

  private static final HttpServer SERVER;
  private static final List<String> AUTH_HEADERS = new CopyOnWriteArrayList<>();
  private static final java.util.concurrent.atomic.AtomicBoolean FAILING =
      new java.util.concurrent.atomic.AtomicBoolean();

  static {
    try {
      SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      SERVER.createContext(
          "/service/v1/read-projection",
          exchange -> {
            AUTH_HEADERS.add(
                exchange.getRequestHeaders().getFirst("Authorization")
                    + "|"
                    + exchange.getRequestHeaders().getFirst("X-Jinguan-Service"));
            if (FAILING.get()) {
              exchange.sendResponseHeaders(500, -1);
              exchange.close();
              return;
            }
            String payload =
                "{\"service_id\":\"kongming\",\"version\":\"42\",\"etag\":\"k-42\","
                    + "\"observed_at\":\""
                    + Instant.now()
                    + "\",\"sections\":{"
                    + "\"bots\":[{\"bot_key\":\"bot-1\",\"token\":\"super-secret\"}],"
                    + "\"data_sources\":[{\"name\":\"pg-main\",\"password\":\"hunter2\"}]}}";
            byte[] response = payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
              out.write(response);
            }
          });
      SERVER.start();
    } catch (IOException e) {
      throw new IllegalStateException("无法启动测试服务只读 API", e);
    }
  }

  @DynamicPropertySource
  static void readProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "jinguan.services.read.base-urls.kongming",
        () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired com.longzhong.jinguan.projection.ProjectionService projectionService;

  String adminToken;

  @BeforeAll
  void login() {
    adminToken = issue("projection-admin", "ADMIN").access();
  }

  @Test
  @Order(1)
  void projectionIsFetchedWithServiceIdentityAndSanitized() throws Exception {
    MvcResult view = adminGet(adminToken, "/admin/services/kongming/projection");
    expectStatus(200, view);
    expectJson(view, "/state", "FRESH");
    expectJson(view, "/sections/bots/0/bot_key", "bot-1");
    expectTrue("***".equals(jsonString(view, "/sections/bots/0/token")), "token 字段必须脱敏");
    expectTrue(
        "***".equals(jsonString(view, "/sections/data_sources/0/password")), "password 字段必须脱敏");
    String stored =
        jdbc.queryForObject(
            "SELECT payload_json FROM service_projections WHERE service_id = 'kongming'",
            String.class);
    expectTrue(stored != null && !stored.contains("hunter2"), "缓存不得保留凭据原值");
    expectTrue(
        AUTH_HEADERS.stream().anyMatch(h -> h.contains("Bearer read-token|kongming")),
        "出站调用必须携带服务身份: " + AUTH_HEADERS);
  }

  @Test
  @Order(2)
  void staleLastKnownUsedWhenServiceFails() throws Exception {
    jdbc.update(
        "UPDATE service_projections SET fetched_at = ?, observed_at = ?"
            + " WHERE service_id = 'kongming'",
        Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)),
        Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)));
    FAILING.set(true);
    try {
      MvcResult stale = adminGet(adminToken, "/admin/services/kongming/projection");
      expectStatus(200, stale);
      expectJson(stale, "/state", "STALE");
      expectJson(stale, "/sections/bots/0/bot_key", "bot-1");
    } finally {
      FAILING.set(false);
    }
  }

  @Test
  @Order(3)
  void missingMappingShowsUnknownNotGuessed() throws Exception {
    MvcResult unknown = adminGet(adminToken, "/admin/services/muniu/projection");
    expectStatus(200, unknown);
    expectJson(unknown, "/state", "UNKNOWN");
    expectJson(unknown, "/mappingConfigured", "false");
    expectStatus(404, adminGet(adminToken, "/admin/services/muniu/projection/export"));
  }

  @Test
  @Order(4)
  void exportIsAuditedAndSanitized() throws Exception {
    MvcResult exported = adminGet(adminToken, "/admin/services/kongming/projection/export");
    expectStatus(200, exported);
    expectTextContains(exported, "bot-1");
    expectNotContains(exported, "hunter2");
    expectTrue(
        body(adminGet(adminToken, "/admin/audit-events?action=PROJECTION_EXPORT")).size() >= 1,
        "导出必须审计");
    expectTrue(
        body(adminGet(adminToken, "/admin/audit-events?action=PROJECTION_VIEW")).size() >= 1,
        "详情查看必须审计");
  }

  @Test
  @Order(5)
  void retentionPurgesExpiredProjectionWithoutResidue() throws Exception {
    jdbc.update(
        "UPDATE service_projections SET fetched_at = ? WHERE service_id = 'kongming'",
        Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS)));
    int purged = projectionService.purgeExpired(Instant.now().minus(5, ChronoUnit.DAYS));
    expectTrue(purged >= 1, "超过保留期的投影应被清理");
    Integer remaining =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM service_projections WHERE service_id = 'kongming'",
            Integer.class);
    expectTrue(remaining != null && remaining == 0, "清理后不得残留投影缓存");
    MvcResult unknown = adminGet(adminToken, "/admin/services/kongming/projection");
    expectStatus(200, unknown);
    expectJson(unknown, "/state", "FRESH");
  }
}
