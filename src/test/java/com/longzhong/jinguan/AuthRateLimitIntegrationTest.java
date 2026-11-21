package com.longzhong.jinguan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** OIDC 登录入口按 IP 限流；超限返回 429。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-ratelimit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.security.rate-limit.login-per-minute=2",
    })
class AuthRateLimitIntegrationTest {

  @Autowired MockMvc mvc;

  @Test
  void oidcStartIsRateLimited() throws Exception {
    assertEquals(
        404,
        mvc.perform(get("/auth/oidc/start")).andReturn().getResponse().getStatus(),
        "未启用应 404");
    assertEquals(
        404,
        mvc.perform(get("/auth/oidc/start")).andReturn().getResponse().getStatus(),
        "未启用应 404");
    assertEquals(
        429,
        mvc.perform(get("/auth/oidc/start")).andReturn().getResponse().getStatus(),
        "同窗口第 3 次必须 429");
  }
}
