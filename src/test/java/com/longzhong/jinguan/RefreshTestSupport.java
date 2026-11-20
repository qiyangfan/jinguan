package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.longzhong.jinguan.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** refresh 集成测试公共支撑:注册、续期、cookie 与 jti 解析。 */
class RefreshTestSupport {

  static final String REFRESH_COOKIE = "jinguan.refresh";

  @Autowired MockMvc mvc;
  @Autowired JwtService jwtService;

  String register(String username) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + username + "\",\"password\":\"password-123\"}"))
            .andReturn();
    Assertions.assertEquals(HttpStatus.CREATED.value(), result.getResponse().getStatus(), "注册必须成功");
    return cookieValue(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
  }

  MvcResult refresh(String refreshValue) throws Exception {
    return mvc.perform(post("/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, refreshValue)))
        .andReturn();
  }

  /** 断言续期成功并返回新 refresh 票。 */
  String rotatedCookie(MvcResult result) throws Exception {
    Assertions.assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus(), "续期必须 200");
    return cookieValue(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
  }

  String jti(String refreshToken) {
    return jwtService.verifyRefresh(refreshToken).jti();
  }

  static String cookieValue(String setCookie) {
    Assertions.assertNotNull(setCookie, "必须下发 refresh cookie");
    return setCookie.substring((REFRESH_COOKIE + "=").length(), setCookie.indexOf(';'));
  }
}
