package com.longzhong.jinguan.user;

import com.longzhong.jinguan.common.RefreshReuseException;
import com.longzhong.jinguan.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本地会话：refresh 轮换与登出。登录入口只有Keycloak OIDC。 */
@RestController
public class AuthController {

  private final PrincipalService principals;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokens;
  private final AuthCookies cookies;

  public AuthController(
      PrincipalService principals,
      JwtService jwtService,
      RefreshTokenService refreshTokens,
      AuthCookies cookies) {
    this.principals = principals;
    this.jwtService = jwtService;
    this.refreshTokens = refreshTokens;
    this.cookies = cookies;
  }

  /** 校验 refresh cookie 并轮换双 token；无效、禁用或重放时拒绝。 */
  @PostMapping("/auth/refresh")
  public ResponseEntity<Object> refresh(
      @CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshCookie,
      HttpServletResponse response) {
    JwtService.LoginUser presented =
        refreshCookie == null ? null : jwtService.verifyRefresh(refreshCookie);
    if (presented == null) {
      return unauthorized("refresh 凭证无效");
    }
    PrincipalEntity principal;
    try {
      principals.assertEnabled(presented.subject());
      principal = principals.get(presented.subject());
    } catch (com.longzhong.jinguan.common.AccountDisabledException e) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "账号不可用"));
    } catch (RuntimeException e) {
      return unauthorized("账号不可用");
    }
    JwtService.LoginUser current =
        new JwtService.LoginUser(
            principal.getSubject(),
            principal.getUsername(),
            principal.getRole(),
            presented.jti(),
            presented.expiresAt());
    String newRefresh = refreshTokens.rotate(current);
    if (newRefresh == null) {
      throw new RefreshReuseException();
    }
    response.addHeader(HttpHeaders.SET_COOKIE, cookies.refresh(newRefresh).toString());
    return ResponseEntity.ok(
        Map.of(
            "token",
            jwtService.issueAccess(
                principal.getSubject(), principal.getUsername(), principal.getRole())));
  }

  /** 清除 refresh cookie；携带有效 refresh 时吊销该主体全部会话。 */
  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshCookie,
      HttpServletResponse response) {
    revokePresented(refreshCookie);
    response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
    return ResponseEntity.noContent().build();
  }

  public void revokePresented(String refreshCookie) {
    if (refreshCookie == null) {
      return;
    }
    JwtService.LoginUser user = jwtService.verifyRefresh(refreshCookie);
    if (user != null) {
      refreshTokens.revokeAll(user.subject());
    }
  }

  private ResponseEntity<Object> unauthorized(String detail) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail));
  }
}
