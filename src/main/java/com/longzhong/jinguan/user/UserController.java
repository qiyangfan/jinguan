package com.longzhong.jinguan.user;

import com.longzhong.jinguan.common.RefreshReuseException;
import com.longzhong.jinguan.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 认证入口：access token 通过响应体返回，refresh token 使用 httpOnly、SameSite=Strict cookie。 */
@RestController
@Validated
public class UserController {

  public static final String REFRESH_COOKIE = "jinguan.refresh";

  private final UserService users;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokens;
  private final boolean refreshCookieSecure;
  private final long refreshTtlDays;

  public UserController(
      UserService users,
      JwtService jwtService,
      RefreshTokenService refreshTokens,
      @Value("${jinguan.security.refresh-cookie-secure}") boolean refreshCookieSecure,
      @Value("${jinguan.security.jwt-refresh-ttl-days}") long refreshTtlDays) {
    this.users = users;
    this.jwtService = jwtService;
    this.refreshTokens = refreshTokens;
    this.refreshCookieSecure = refreshCookieSecure;
    this.refreshTtlDays = refreshTtlDays;
  }

  /** 用户名与密码凭据体。 */
  public record Credentials(
      @NotBlank @Size(min = 3, max = 64) String username,
      @NotBlank @Size(min = 8, max = 72) String password) {}

  @PostMapping("/auth/register")
  public ResponseEntity<Map<String, String>> register(
      @RequestBody @Validated Credentials req, HttpServletResponse response) {
    UserService.AuthTokens tokens = users.register(req.username(), req.password());
    recordRefresh(tokens.refresh());
    response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refresh()).toString());
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("token", tokens.access()));
  }

  @PostMapping("/auth/login")
  public Map<String, String> login(
      @RequestBody @Validated Credentials req, HttpServletResponse response) {
    UserService.AuthTokens tokens = users.login(req.username(), req.password());
    recordRefresh(tokens.refresh());
    response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refresh()).toString());
    return Map.of("token", tokens.access());
  }

  /** 校验 refresh cookie 并轮换双 token；无效或重放时返回 401。 */
  @PostMapping("/auth/refresh")
  public ResponseEntity<Object> refresh(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie,
      HttpServletResponse response) {
    JwtService.LoginUser user =
        refreshCookie == null ? null : jwtService.verifyRefresh(refreshCookie);
    if (user == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "refresh 凭证无效"));
    }
    // 续期前校验账号未禁用；禁用账号不得换发新令牌。
    users.assertEnabled(user.uid());
    // 消费旧票并轮换；宽限内重复提交返回同一后继票，超窗重放吊销全家族。
    String newRefresh = refreshTokens.rotate(user);
    if (newRefresh == null) {
      throw new RefreshReuseException();
    }
    response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(newRefresh).toString());
    return ResponseEntity.ok(
        Map.of("token", jwtService.issueAccess(user.uid(), user.getName(), user.role())));
  }

  /** 清除 refresh cookie；携带有效 refresh 时同时吊销该用户全部会话。 */
  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie,
      HttpServletResponse response) {
    if (refreshCookie != null) {
      JwtService.LoginUser user = jwtService.verifyRefresh(refreshCookie);
      if (user != null) {
        refreshTokens.revokeAll(user.uid());
      }
    }
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(REFRESH_COOKIE, "")
            .httpOnly(true)
            .secure(refreshCookieSecure)
            .sameSite(Cookie.SameSite.STRICT.attributeValue())
            .path("/auth")
            .maxAge(0)
            .build()
            .toString());
    return ResponseEntity.noContent().build();
  }

  private void recordRefresh(String refresh) {
    JwtService.LoginUser info = jwtService.verifyRefresh(refresh);
    if (info != null) {
      refreshTokens.record(info.jti(), info.uid(), info.expiresAt());
    }
  }

  private ResponseCookie refreshCookie(String value) {
    return ResponseCookie.from(REFRESH_COOKIE, value)
        .httpOnly(true)
        .secure(refreshCookieSecure)
        .sameSite(Cookie.SameSite.STRICT.attributeValue())
        .path("/auth")
        .maxAge(Duration.ofDays(refreshTtlDays))
        .build();
  }
}
