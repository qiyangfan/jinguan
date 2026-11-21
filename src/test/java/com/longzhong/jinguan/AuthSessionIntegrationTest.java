package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.longzhong.jinguan.user.AuthCookies;
import com.longzhong.jinguan.user.TokenIssuer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** 验证本地双 token 的续期、鉴权与登出；本地账密端点必须不存在。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
    })
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class AuthSessionIntegrationTest extends RefreshTestSupport {

  private static final String AUTHORIZATION = "Authorization";
  private static final String BEARER = "Bearer ";
  private static final String TOKEN_PATH = "$.token";

  String accessToken;
  String refreshValue;

  @Test
  @Order(1)
  void issuedDualTokenAuthenticatesAndRefreshCookieIsHardened() throws Exception {
    TokenIssuer.TokenPair pair = issue("auth-u1", "USER");
    accessToken = pair.access();
    refreshValue = pair.refresh();
    MvcResult byAccess =
        mvc.perform(get("/wallet/balance").header(AUTHORIZATION, BEARER + accessToken)).andReturn();
    Assertions.assertEquals(200, byAccess.getResponse().getStatus(), "access token 必须可访问 API");
    MvcResult byRefresh =
        mvc.perform(get("/wallet/balance").header(AUTHORIZATION, BEARER + refreshValue))
            .andReturn();
    Assertions.assertEquals(403, byRefresh.getResponse().getStatus(), "refresh token 不得作为 Bearer");
  }

  @Test
  @Order(2)
  void refreshRotatesBothTokens() throws Exception {
    MvcResult result =
        mvc.perform(
                post("/auth/refresh").cookie(new Cookie(AuthCookies.REFRESH_COOKIE, refreshValue)))
            .andExpect(status().isOk())
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        TOKEN_PATH)
                    .exists())
            .andReturn();
    String newAccess = JsonPath.read(result.getResponse().getContentAsString(), TOKEN_PATH);
    Assertions.assertNotEquals(accessToken, newAccess, "轮换必须签发新 access");
    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    Assertions.assertNotNull(setCookie, "轮换必须下发新 refresh cookie");
    Assertions.assertTrue(setCookie.contains("HttpOnly"), "cookie 必须 HttpOnly");
    Assertions.assertTrue(setCookie.contains("SameSite=Strict"), "cookie 必须 SameSite=Strict");
    Assertions.assertTrue(setCookie.contains("Path=/auth"), "cookie 必须 Path=/auth");
    String newRefresh = cookieValue(setCookie);
    Assertions.assertNotEquals(refreshValue, newRefresh, "轮换必须签发新 refresh");
    mvc.perform(get("/wallet/balance").header(AUTHORIZATION, BEARER + newAccess))
        .andExpect(status().isOk());
    accessToken = newAccess;
    refreshValue = newRefresh;
  }

  @Test
  @Order(3)
  void refreshWithoutOrInvalidCookieRejected() throws Exception {
    MvcResult missing = mvc.perform(post("/auth/refresh")).andReturn();
    Assertions.assertEquals(401, missing.getResponse().getStatus(), "缺少 refresh cookie 应 401");
    MvcResult wrongToken =
        mvc.perform(
                post("/auth/refresh").cookie(new Cookie(AuthCookies.REFRESH_COOKIE, accessToken)))
            .andReturn();
    Assertions.assertEquals(401, wrongToken.getResponse().getStatus(), "access token 不能用于 refresh");
  }

  @Test
  @Order(4)
  void logoutClearsCookieAndRevokesRefresh() throws Exception {
    MvcResult result =
        mvc.perform(
                post("/auth/logout").cookie(new Cookie(AuthCookies.REFRESH_COOKIE, refreshValue)))
            .andExpect(status().isNoContent())
            .andReturn();
    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    Assertions.assertNotNull(setCookie, "登出必须下发清除 cookie");
    Assertions.assertTrue(setCookie.contains("Max-Age=0"), "登出必须以 Max-Age=0 清 cookie");
    Assertions.assertEquals(
        401, refresh(refreshValue).getResponse().getStatus(), "登出后 refresh 必须失效");
  }

  @Test
  @Order(5)
  void localPasswordEndpointsAreGone() throws Exception {
    MvcResult login =
        mvc.perform(
                post("/auth/login")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"u\",\"password\":\"password-123\"}"))
            .andReturn();
    int loginStatus = login.getResponse().getStatus();
    Assertions.assertTrue(
        loginStatus == 404 || loginStatus == 405, "本地登录端点必须不存在,实际:" + loginStatus);
    MvcResult register =
        mvc.perform(
                post("/auth/register")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"u\",\"password\":\"password-123\"}"))
            .andReturn();
    int registerStatus = register.getResponse().getStatus();
    Assertions.assertTrue(
        registerStatus == 404 || registerStatus == 405, "本地注册端点必须不存在,实际:" + registerStatus);
  }

  @Test
  @Order(6)
  void spaDeepLinksAnonymousReachableForEveryFrontendRoute() throws Exception {
    for (String route :
        new String[] {"/wallet", "/payments", "/pricing", "/topology", "/keys", "/login"}) {
      MvcResult result = mvc.perform(get(route)).andReturn();
      int s = result.getResponse().getStatus();
      Assertions.assertTrue(s == 200 || s == 404, route + " 必须 200(index 回退)或 404(无前端产物),实际:" + s);
    }
  }
}
