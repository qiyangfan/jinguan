package com.longzhong.jinguan;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 双 token 会话端到端(单进程,H2):注册双发(access 体 + refresh httpOnly SameSite=Strict Path=/auth cookie) →
 * refresh 轮换(新 access + 新 refresh,旧值失效性靠短寿命与轮换)→ access 认 API、refresh 不可当 Bearer → 登出清
 * cookie。已知限制:复用检测未做(openspec auth-session 登记)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.security.jwt-secret=test-secret-0123456789abcdef-0123456789abcdef",
    })
class AuthSessionIntegrationTest {

  @Autowired MockMvc mvc;

  static String accessToken;
  static String refreshValue;

  @Test
  @Order(1)
  void register_issuesDualToken_withHardenedCookie() throws Exception {
    MvcResult result =
        mvc.perform(
                post("/auth/register")
                    .contentType("application/json")
                    .content("{\"username\":\"auth-u1\",\"password\":\"password-123\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").exists())
            .andReturn();
    accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    String setCookie = result.getResponse().getHeader("Set-Cookie");
    assertNotNull(setCookie, "注册必须下发 refresh cookie");
    assertTrue(setCookie.startsWith("jinguan.refresh="), "cookie 名固定 jinguan.refresh");
    assertTrue(setCookie.contains("HttpOnly"), "cookie 必须 HttpOnly(JS 不可读)");
    assertTrue(setCookie.contains("SameSite=Strict"), "cookie 必须 SameSite=Strict");
    assertTrue(setCookie.contains("Path=/auth"), "cookie 必须 Path=/auth(仅认证端点携带)");
    refreshValue = setCookie.substring("jinguan.refresh=".length(), setCookie.indexOf(';'));
  }

  @Test
  @Order(2)
  void accessAuthenticates_refreshCannotBearer() throws Exception {
    // access 认 API
    mvc.perform(get("/wallet/balance").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());
    // refresh 凭证(typ=refresh)不可当 Bearer 使用
    mvc.perform(get("/wallet/balance").header("Authorization", "Bearer " + refreshValue))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(3)
  void refresh_rotates_bothTokens() throws Exception {
    MvcResult result =
        mvc.perform(post("/auth/refresh").cookie(new Cookie("jinguan.refresh", refreshValue)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists())
            .andReturn();
    String newAccess = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    assertNotEquals(accessToken, newAccess, "轮换必须签发新 access");
    String setCookie = result.getResponse().getHeader("Set-Cookie");
    assertNotNull(setCookie);
    String newRefresh = setCookie.substring("jinguan.refresh=".length(), setCookie.indexOf(';'));
    assertNotEquals(refreshValue, newRefresh, "轮换必须签发新 refresh");
    // 新 access 可用
    mvc.perform(get("/wallet/balance").header("Authorization", "Bearer " + newAccess))
        .andExpect(status().isOk());
    accessToken = newAccess;
  }

  @Test
  @Order(4)
  void refresh_withoutOrInvalidCookie_rejected() throws Exception {
    // 无 cookie
    mvc.perform(post("/auth/refresh")).andExpect(status().isUnauthorized());
    // access 冒充 refresh(typ 不符)
    mvc.perform(post("/auth/refresh").cookie(new Cookie("jinguan.refresh", accessToken)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @Order(5)
  void logout_clearsCookie() throws Exception {
    MvcResult result =
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent())
            .andReturn();
    String setCookie = result.getResponse().getHeader("Set-Cookie");
    assertNotNull(setCookie);
    assertTrue(setCookie.contains("Max-Age=0"), "登出必须以 Max-Age=0 清 cookie");
  }

  @Test
  @Order(6)
  void malformedBody_is400ProblemDetail_not403Empty() throws Exception {
    // Jackson 3 FAIL_ON_NULL_FOR_PRIMITIVES:缺 primitive 字段的请求体必须以 400 ProblemDetail
    // 出口;若走 sendError→ERROR dispatch /error,Security 7 全 dispatch 授权会顶成 403 空体
    mvc.perform(
            post("/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"u\",\"password\":123456}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").exists())
        .andExpect(jsonPath("$.title").value("Bad Request"));
  }

  @Test
  @Order(7)
  void spaDeepLinks_anonymousReachable_forEveryFrontendRoute() throws Exception {
    // 深链/刷新回退 index.html 由前端路由接管:白名单必须覆盖前端全部路由,
    // 漏项即该页刷新 403(拓扑页上线时曾漏 /topology,2026-10-18 走查实证后补)
    for (String route :
        new String[] {"/wallet", "/payments", "/pricing", "/topology", "/keys", "/login"}) {
      mvc.perform(get(route))
          .andExpect(status().isOk())
          .andExpect(
              result -> {
                String ct = result.getResponse().getContentType();
                assertNotNull(ct, route + " 必须有 Content-Type");
                assertTrue(ct.contains("text/html"), route + " 应回退 index.html,实际:" + ct);
              });
    }
  }
}
