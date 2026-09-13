package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 登录防爆破(per-IP 固定窗口):超限 429,窗口滑过恢复——独立小容量上下文钉死边界。 MockMvc 的 remoteAddr 恒为 127.0.0.1,恰好构成同一 IP
 * 的最坏情形。
 */
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
    // 第 3 次:同 IP 同窗口超限
    mvc.perform(
            post("/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"ghost\",\"password\":\"password-123\"}"))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void register_hasIndependentBucket() throws Exception {
    // 与 login 桶独立:注册走自己的窗口(本类 2/min),注册本身不限流阻断(前 2 次)
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
    // 第 3 次注册:同窗口超限
    mvc.perform(
            post("/auth/register")
                .contentType("application/json")
                .content("{\"username\":\"rl-u3\",\"password\":\"password-123\"}"))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void getEndpoint_notLimited() throws Exception {
    // 非认证写端点不受限流(健康检查匿名 GET 打 401 之外的路径无关紧要——这里验证不受 429)
    for (int i = 0; i < 5; i++) {
      mvc.perform(post("/auth/refresh")).andExpect(status().isUnauthorized());
    }
  }
}
