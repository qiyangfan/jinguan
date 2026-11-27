package com.longzhong.jinguan;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** 控制面抗滥用与调度器租约:429/Retry-After、413、单实例租约。 */
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-control-ratelimit;MODE=PostgreSQL;"
          + "DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.service-auth.local-token=test-service-token",
      "jinguan.alerts.operational-token=ops-token",
      "jinguan.control.rate-limit.per-minute=3",
      "jinguan.control.max-request-bytes=1024"
    })
// 断言集中在 ControlTestSupport.expect*;限流按固定测试 IP 分桶。
@SuppressWarnings({
  "PMD.UnitTestShouldIncludeAssert",
  "PMD.AvoidUsingHardCodedIP",
  "PMD.AvoidDuplicateLiterals"
})
class ControlRateLimitIntegrationTest extends ControlTestSupport {

  @Autowired JdbcTemplate jdbc;
  @Autowired com.longzhong.jinguan.lease.SchedulerLeaseRepository leaseRepository;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
  @Autowired com.longzhong.jinguan.lease.LeaseService leaseService;

  String adminToken;

  @BeforeAll
  void login() {
    adminToken = issue("limit-admin", "ADMIN").access();
  }

  @Test
  @Order(1)
  void perIpRateLimitReturns429WithRetryAfter() throws Exception {
    expectStatus(200, getFrom("/admin/audit-events", "10.9.9.1"));
    expectStatus(200, getFrom("/admin/audit-events", "10.9.9.1"));
    expectStatus(200, getFrom("/admin/audit-events", "10.9.9.1"));
    MvcResult limited = getFrom("/admin/audit-events", "10.9.9.1");
    expectStatus(429, limited);
    expectTrue("60".equals(limited.getResponse().getHeader("Retry-After")), "429 必须带 Retry-After");
    Integer audited =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM control_audit_log WHERE action = 'RATE_LIMITED'", Integer.class);
    expectTrue(audited != null && audited >= 1, "限流必须审计");
    expectTypeOf(limited, "application/problem+json");
  }

  @Test
  @Order(2)
  void perServiceRateLimitIsIndependent() throws Exception {
    for (int i = 0; i < 3; i++) {
      expectStatus(200, serviceGetFrom("kongming", "/control/v1/snapshot", "127.0.0.1"));
    }
    MvcResult limited = serviceGetFrom("kongming", "/control/v1/snapshot", "127.0.0.1");
    expectStatus(429, limited);
    expectTrue("60".equals(limited.getResponse().getHeader("Retry-After")), "服务维度同样带 Retry-After");
    expectStatus(200, getFrom("/admin/audit-events", "10.9.9.9"));
  }

  @Test
  @Order(3)
  void oversizedControlRequestIsRejectedWith413() throws Exception {
    String bigName = "x".repeat(2000);
    MvcResult tooLarge =
        mvc.perform(
                postFrom("/admin/tenants", "10.9.9.2")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(
                        "{\"tenantKey\":\"big\",\"name\":\"" + bigName + "\",\"reason\":\"r\"}"))
            .andReturn();
    expectStatus(413, tooLarge);
    Integer created =
        jdbc.queryForObject("SELECT COUNT(*) FROM tenants WHERE tenant_key = 'big'", Integer.class);
    expectTrue(created != null && created == 0, "超限请求不得落库");
  }

  @Test
  @Order(4)
  void leaseAllowsSingleOwnerAndExpiresForTakeover() throws Exception {
    com.longzhong.jinguan.lease.LeaseService secondOwner =
        new com.longzhong.jinguan.lease.LeaseService(leaseRepository, transactionManager);
    expectTrue(leaseService.tryAcquire("test-lease", Duration.ofMinutes(5)), "首个持有者应取得租约");
    expectTrue(!secondOwner.tryAcquire("test-lease", Duration.ofMinutes(5)), "第二个持有者应被拒绝");
    jdbc.update(
        "UPDATE scheduler_leases SET expires_at = ? WHERE name = 'test-lease'",
        Timestamp.from(Instant.now().minusSeconds(10)));
    expectTrue(secondOwner.tryAcquire("test-lease", Duration.ofMinutes(5)), "过期租约应可被抢占");
    expectStatus(200, getFrom("/admin/audit-events", "10.9.9.10"));
  }

  private MvcResult getFrom(String path, String remoteAddr) throws Exception {
    return mvc.perform(getTo(path, remoteAddr)).andReturn();
  }

  private MockHttpServletRequestBuilder getTo(String path, String remoteAddr) {
    return MockMvcRequestBuilders.get(path)
        .header("Authorization", "Bearer " + adminToken)
        .with(
            request -> {
              request.setRemoteAddr(remoteAddr);
              return request;
            });
  }

  private MockHttpServletRequestBuilder postFrom(String path, String remoteAddr) {
    return MockMvcRequestBuilders.post(path)
        .header("Authorization", "Bearer " + adminToken)
        .with(
            request -> {
              request.setRemoteAddr(remoteAddr);
              return request;
            });
  }

  private MvcResult serviceGetFrom(String serviceId, String path, String remoteAddr)
      throws Exception {
    return mvc.perform(
            MockMvcRequestBuilders.get(path)
                .header("Authorization", "Bearer " + SERVICE_TOKEN)
                .header(SERVICE_ID_HEADER, serviceId)
                .with(
                    request -> {
                      request.setRemoteAddr(remoteAddr);
                      return request;
                    }))
        .andReturn();
  }

  void expectTypeOf(MvcResult result, String expected) {
    String actual = result.getResponse().getContentType();
    expectTrue(
        actual != null && actual.contains(expected),
        "Content-Type 应为 " + expected + ", 实际 " + actual);
  }
}
