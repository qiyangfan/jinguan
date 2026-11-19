package com.longzhong.jinguan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 验证登录与注册的 per-IP 固定窗口限流及窗口恢复。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-ratelimit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.security.jwt-secret=test-secret-0123456789abcdef-0123456789abcdef",
      "jinguan.security.rate-limit.login-per-minute=2",
      "jinguan.security.rate-limit.register-per-minute=2",
    })
class AuthRateLimitIntegrationTest {

  private static final String GHOST_LOGIN =
      "{\"username\":\"ghost\",\"password\":\"password-123\"}";

  @Autowired MockMvc mvc;

  private MvcResult login() throws Exception {
    return mvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(GHOST_LOGIN))
        .andReturn();
  }

  private MvcResult register(String username) throws Exception {
    return mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"password-123\"}".formatted(username)))
        .andReturn();
  }

  @Test
  void loginExceedsPerIpWindow429() throws Exception {
    for (int i = 0; i < 2; i++) {
      assertEquals(400, login().getResponse().getStatus(), "窗口内前两次应 400");
    }
    // 同一窗口内超限。
    assertEquals(429, login().getResponse().getStatus(), "同窗口第 3 次登录必须 429");
  }

  @Test
  void registerHasIndependentBucket() throws Exception {
    // 注册使用独立限流窗口。
    assertEquals(201, register("rl-u1").getResponse().getStatus(), "首个注册应 201");
    assertEquals(201, register("rl-u2").getResponse().getStatus(), "第二个注册仍应 201");
    // 注册窗口内超限。
    assertEquals(429, register("rl-u3").getResponse().getStatus(), "注册窗口第 3 次应 429");
  }

  @Test
  void endpointNotLimited() throws Exception {
    // 其他写端点不受认证限流器影响。
    for (int i = 0; i < 5; i++) {
      MvcResult refreshed = mvc.perform(post("/auth/refresh")).andReturn();
      assertEquals(401, refreshed.getResponse().getStatus(), "刷新端点应 401");
    }
  }
}
