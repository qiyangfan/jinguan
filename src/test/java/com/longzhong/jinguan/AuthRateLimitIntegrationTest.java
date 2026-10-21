package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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

  @Autowired MockMvc mvc;

  @Test
  void login_exceedsPerIpWindow_429() throws Exception {
    for (int i = 0; i < 2; i++) {
      mvc.perform(
              post("/auth/login")
                  .contentType("application/json")
                  .content("{\"username\":\"ghost\",\"password\":\"password-123\"}"))
          .andExpect(status().isBadRequest());
    }
    // 同一窗口内超限。
    mvc.perform(
            post("/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"ghost\",\"password\":\"password-123\"}"))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void register_hasIndependentBucket() throws Exception {
    // 注册使用独立限流窗口。
    mvc.perform(
            post("/auth/register")
                .contentType("application/json")
                .content("{\"username\":\"rl-u1\",\"password\":\"password-123\"}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/auth/register")
                .contentType("application/json")
                .content("{\"username\":\"rl-u2\",\"password\":\"password-123\"}"))
        .andExpect(status().isCreated());
    // 注册窗口内超限。
    mvc.perform(
            post("/auth/register")
                .contentType("application/json")
                .content("{\"username\":\"rl-u3\",\"password\":\"password-123\"}"))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void getEndpoint_notLimited() throws Exception {
    // 其他写端点不受认证限流器影响。
    for (int i = 0; i < 5; i++) {
      mvc.perform(post("/auth/refresh")).andExpect(status().isUnauthorized());
    }
  }
}
