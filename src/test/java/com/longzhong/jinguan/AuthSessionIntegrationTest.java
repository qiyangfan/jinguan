package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/** 验证 access/refresh 双 token 的注册、续期、鉴权和登出流程。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.security.jwt-secret=test-secret-0123456789abcdef-0123456789abcdef",
    })
class AuthSessionIntegrationTest {

  private static final String REFRESH_COOKIE = "jinguan.refresh";
  private static final String AUTHORIZATION = "Authorization";
  private static final String BEARER = "Bearer ";
  private static final String TOKEN_PATH = "$.token";

  @Autowired MockMvc mvc;

  String accessToken;
  String refreshValue;

  @Test
  @Order(1)
  void registerIssuesDualTokenWithHardenedCookie() throws Exception {
    MvcResult result =
        mvc.perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"auth-u1\",\"password\":\"password-123\"}"))
            .andExpect(status().isCreated())
            .andExpect(MockMvcResultMatchers.jsonPath(TOKEN_PATH).exists())
            .andReturn();
    accessToken = JsonPath.read(result.getResponse().getContentAsString(), TOKEN_PATH);
    String setCookie = result.getResponse().getHeader("Set-Cookie");
    Assertions.assertNotNull(setCookie, "注册必须下发 refresh cookie");
    Assertions.assertTrue(setCookie.startsWith(REFRESH_COOKIE + "="), "cookie 名固定 jinguan.refresh");
    Assertions.assertTrue(setCookie.contains("HttpOnly"), "cookie 必须 HttpOnly(JS 不可读)");
    Assertions.assertTrue(setCookie.contains("SameSite=Strict"), "cookie 必须 SameSite=Strict");
    Assertions.assertTrue(setCookie.contains("Path=/auth"), "cookie 必须 Path=/auth(仅认证端点携带)");
    refreshValue = cookieValue(setCookie);
  }

  @Test
  @Order(2)
  void accessAuthenticatesRefreshCannotBearer() throws Exception {
    // access token 可访问 API。
    MvcResult byAccess =
        mvc.perform(get("/wallet/balance").header(AUTHORIZATION, BEARER + accessToken)).andReturn();
    Assertions.assertEquals(200, byAccess.getResponse().getStatus(), "access token 必须可访问 API");
    // refresh token 不可作为 Bearer token。
    MvcResult byRefresh =
        mvc.perform(get("/wallet/balance").header(AUTHORIZATION, BEARER + refreshValue))
            .andReturn();
    Assertions.assertEquals(403, byRefresh.getResponse().getStatus(), "refresh token 不得作为 Bearer");
  }

  @Test
  @Order(3)
  void refreshRotatesBothTokens() throws Exception {
    MvcResult result =
        mvc.perform(post("/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, refreshValue)))
            .andExpect(status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath(TOKEN_PATH).exists())
            .andReturn();
    String newAccess = JsonPath.read(result.getResponse().getContentAsString(), TOKEN_PATH);
    Assertions.assertNotEquals(accessToken, newAccess, "轮换必须签发新 access");
    String setCookie = result.getResponse().getHeader("Set-Cookie");
    Assertions.assertNotNull(setCookie, "轮换必须下发新 refresh cookie");
    String newRefresh = cookieValue(setCookie);
    Assertions.assertNotEquals(refreshValue, newRefresh, "轮换必须签发新 refresh");
    // 新 access token 可用。
    mvc.perform(get("/wallet/balance").header(AUTHORIZATION, BEARER + newAccess))
        .andExpect(status().isOk());
    accessToken = newAccess;
  }

  @Test
  @Order(4)
  void refreshWithoutOrInvalidCookieRejected() throws Exception {
    // 缺少 refresh cookie。
    MvcResult missing = mvc.perform(post("/auth/refresh")).andReturn();
    Assertions.assertEquals(401, missing.getResponse().getStatus(), "缺少 refresh cookie 应 401");
    // access token 不能用于 refresh。
    MvcResult wrongToken =
        mvc.perform(post("/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, accessToken)))
            .andReturn();
    Assertions.assertEquals(401, wrongToken.getResponse().getStatus(), "access token 不能用于 refresh");
  }

  @Test
  @Order(5)
  void logoutClearsCookie() throws Exception {
    MvcResult result =
        mvc.perform(post("/auth/logout").header(AUTHORIZATION, BEARER + accessToken))
            .andExpect(status().isNoContent())
            .andReturn();
    String setCookie = result.getResponse().getHeader("Set-Cookie");
    Assertions.assertNotNull(setCookie, "登出必须下发清除 cookie");
    Assertions.assertTrue(setCookie.contains("Max-Age=0"), "登出必须以 Max-Age=0 清 cookie");
  }

  @Test
  @Order(6)
  void malformedBodyIs400ProblemDetailNot403Empty() throws Exception {
    // 缺少 primitive 字段返回 400，错误分发不得被安全规则改写成 403。
    MvcResult bad =
        mvc.perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"u\",\"password\":123456}"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.detail").exists())
            .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Bad Request"))
            .andReturn();
    Assertions.assertEquals(400, bad.getResponse().getStatus(), "畸形请求体必须 400 而非 403 空体");
  }

  @Test
  @Order(7)
  void spaDeepLinksAnonymousReachableForEveryFrontendRoute() throws Exception {
    // 深链需回退到前端入口；无构建产物时允许 404，但不得返回 403 或 5xx。
    int okStatus = 200;
    int notFoundStatus = 404;
    for (String route :
        new String[] {"/wallet", "/payments", "/pricing", "/topology", "/keys", "/login"}) {
      MvcResult result = mvc.perform(get(route)).andReturn();
      int s = result.getResponse().getStatus();
      Assertions.assertTrue(
          s == okStatus || s == notFoundStatus, route + " 必须 200(index 回退)或 404(无前端产物),实际:" + s);
      if (s == okStatus) {
        String ct = result.getResponse().getContentType();
        Assertions.assertNotNull(ct, route + " 必须有 Content-Type");
        Assertions.assertTrue(ct.contains("text/html"), route + " 应回退 index.html,实际:" + ct);
      }
    }
  }

  private static String cookieValue(String setCookie) {
    return setCookie.substring((REFRESH_COOKIE + "=").length(), setCookie.indexOf(';'));
  }
}
