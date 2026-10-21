package com.longzhong.jinguan.user;

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
  private final boolean refreshCookieSecure;
  private final long refreshTtlDays;

  public UserController(
      UserService users,
      JwtService jwtService,
      @Value("${jinguan.security.refresh-cookie-secure}") boolean refreshCookieSecure,
      @Value("${jinguan.security.jwt-refresh-ttl-days}") long refreshTtlDays) {
    this.users = users;
    this.jwtService = jwtService;
    this.refreshCookieSecure = refreshCookieSecure;
    this.refreshTtlDays = refreshTtlDays;
  }

  public record Credentials(
      @NotBlank @Size(min = 3, max = 64) String username,
      @NotBlank @Size(min = 8, max = 72) String password) {}

  @PostMapping("/auth/register")
  public ResponseEntity<Map<String, String>> register(
      @RequestBody @Validated Credentials req, HttpServletResponse response) {
    UserService.AuthTokens tokens = users.register(req.username(), req.password());
    response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refresh()).toString());
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("token", tokens.access()));
  }

  @PostMapping("/auth/login")
  public Map<String, String> login(
      @RequestBody @Validated Credentials req, HttpServletResponse response) {
    UserService.AuthTokens tokens = users.login(req.username(), req.password());
    response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refresh()).toString());
    return Map.of("token", tokens.access());
  }

  /** 校验 refresh cookie 并轮换双 token；无效时返回 401。 */
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
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        refreshCookie(jwtService.issueRefresh(user.uid(), user.getName(), user.role())).toString());
    return ResponseEntity.ok(
        Map.of("token", jwtService.issueAccess(user.uid(), user.getName(), user.role())));
  }

  /** 清除 refresh cookie。 */
  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout(HttpServletResponse response) {
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
