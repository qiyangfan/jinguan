package com.longzhong.jinguan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.longzhong.jinguan.security.BreakGlassAuditRepository;
import com.longzhong.jinguan.security.BreakGlassService;
import com.longzhong.jinguan.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** break-glass 默认关闭、凭据校验、短时 ADMIN 与审计。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-breakglass;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.break-glass.enabled=true",
      "jinguan.break-glass.token=ops-secret-token",
      "jinguan.break-glass.ttl-minutes=5",
      "jinguan.security.rate-limit.login-per-minute=100",
    })
class BreakGlassIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JwtService jwt;
  @Autowired BreakGlassAuditRepository audits;

  @Test
  void correctTokenIssuesShortAdminAndAudits() throws Exception {
    String body =
        mvc.perform(post("/auth/break-glass").header("X-Break-Glass-Token", "ops-secret-token"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = body.substring(body.indexOf(':') + 2, body.length() - 2);
    var user = jwt.verifyAccess(token);
    assertEquals(BreakGlassService.SUBJECT, user.subject(), "签发主体固定");
    assertEquals("ADMIN", user.role(), "break-glass 必须 ADMIN");
    assertTrue(user.expiresAt().isBefore(java.time.Instant.now().plusSeconds(301)), "TTL 必须短时");
    assertEquals(1, audits.count(), "每次签发必须落审计");
  }

  @Test
  void wrongTokenRejected() throws Exception {
    assertEquals(
        401,
        mvc.perform(post("/auth/break-glass").header("X-Break-Glass-Token", "wrong"))
            .andReturn()
            .getResponse()
            .getStatus(),
        "错误凭据必须 401");
  }
}
