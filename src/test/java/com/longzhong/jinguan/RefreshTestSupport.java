package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.longzhong.jinguan.security.JwtService;
import com.longzhong.jinguan.user.PrincipalEntity;
import com.longzhong.jinguan.user.PrincipalRepository;
import com.longzhong.jinguan.user.TokenIssuer;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** refresh 集成测试公共支撑:直接铸造 principal 与双 token,不再经过本地登录端点。 */
public class RefreshTestSupport {

  static final String REFRESH_COOKIE = "jinguan.refresh";

  @Autowired MockMvc mvc;
  @Autowired JwtService jwtService;
  @Autowired PrincipalRepository principals;
  @Autowired TokenIssuer tokenIssuer;

  public String register(String username) {
    return issue(username, "USER").refresh();
  }

  public String registerAdmin(String username) {
    return issue(username, "ADMIN").access();
  }

  public String accessToken(String username, String role) {
    return issue(username, role).access();
  }

  public TokenIssuer.TokenPair issue(String username, String role) {
    String subject = "sub-" + UUID.randomUUID();
    PrincipalEntity principal = principals.save(PrincipalEntity.jit(subject, username));
    principal.syncRole(role);
    principal = principals.save(principal);
    return tokenIssuer.issue(principal);
  }

  public MvcResult refresh(String refreshValue) throws Exception {
    return mvc.perform(post("/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, refreshValue)))
        .andReturn();
  }

  /** 断言续期成功并返回新 refresh 票。 */
  public String rotatedCookie(MvcResult result) {
    Assertions.assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus(), "续期必须 200");
    return cookieValue(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
  }

  public String jti(String refreshToken) {
    return jwtService.verifyRefresh(refreshToken).jti();
  }

  public static String cookieValue(String setCookie) {
    Assertions.assertNotNull(setCookie, "必须下发 refresh cookie");
    return setCookie.substring((REFRESH_COOKIE + "=").length(), setCookie.indexOf(';'));
  }
}
