package com.longzhong.jinguan.oidc;

import com.longzhong.jinguan.user.AuthController;
import com.longzhong.jinguan.user.AuthCookies;
import com.longzhong.jinguan.user.TokenIssuer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

/** Keycloak OIDC RP 入口、回调与单点登出。 */
@RestController
public class OidcController {

  private static final String STATE_COOKIE = "jinguan.oidc.state";

  private final OidcLoginService login;
  private final AuthCookies cookies;
  private final AuthController authController;
  private final boolean secure;

  public OidcController(
      OidcLoginService login,
      AuthCookies cookies,
      AuthController authController,
      @Value("${jinguan.security.refresh-cookie-secure}") boolean secure) {
    this.login = login;
    this.cookies = cookies;
    this.authController = authController;
    this.secure = secure;
  }

  @GetMapping("/auth/oidc/enabled")
  public Map<String, Object> enabled() {
    return Map.of("enabled", login.enabled());
  }

  @GetMapping("/auth/oidc/start")
  public ResponseEntity<Void> start(
      @RequestParam(required = false) String returnTo, HttpServletRequest request) {
    if (!login.enabled()) {
      return ResponseEntity.notFound().build();
    }
    OidcLoginService.Start start = login.start(returnTo);
    var existing = WebUtils.getCookie(request, STATE_COOKIE);
    List<String> states = OidcStateCookie.parse(existing == null ? null : existing.getValue());
    states.add(start.state());
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(start.location()))
        .header(HttpHeaders.SET_COOKIE, stateCookie(OidcStateCookie.encode(states)).toString())
        .build();
  }

  @GetMapping("/auth/oidc/callback")
  public ResponseEntity<Void> callback(
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String code,
      @CookieValue(name = STATE_COOKIE, required = false) String stateCookie,
      HttpServletResponse response) {
    if (!login.enabled()) {
      return ResponseEntity.notFound().build();
    }
    List<String> states = OidcStateCookie.parse(stateCookie);
    if (state == null || !OidcStateCookie.contains(states, state)) {
      return ResponseEntity.badRequest().build();
    }
    TokenIssuer.TokenPair pair;
    try {
      pair = login.callback(state, code);
    } catch (RuntimeException e) {
      return ResponseEntity.badRequest().build();
    }
    response.addHeader(HttpHeaders.SET_COOKIE, cookies.refresh(pair.refresh()).toString());
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        stateCookie(OidcStateCookie.encode(OidcStateCookie.remove(states, state))).toString());
    String returnTo = login.returnTo(state);
    String target = returnTo == null || returnTo.isBlank() ? login.frontendSuccessUrl() : returnTo;
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
  }

  @GetMapping("/auth/logout/sso")
  public ResponseEntity<Void> logoutSso(
      @CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshCookie) {
    authController.revokePresented(refreshCookie);
    String target = login.endSessionLocation();
    if (target == null || target.isBlank()) {
      target = login.frontendSuccessUrl();
    }
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(target))
        .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
        .build();
  }

  private org.springframework.http.ResponseCookie stateCookie(String value) {
    return org.springframework.http.ResponseCookie.from(STATE_COOKIE, value)
        .httpOnly(true)
        .secure(secure)
        .sameSite(Cookie.SameSite.LAX.attributeValue())
        .path("/auth/oidc")
        .maxAge(value.isEmpty() ? 0 : 600)
        .build();
  }
}
