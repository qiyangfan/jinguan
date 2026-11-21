package com.longzhong.jinguan.user;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.Cookie;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** refresh cookie 的唯一构造点：HttpOnly、SameSite=Strict、仅 /auth 路径。 */
@Component
public class AuthCookies {

  public static final String REFRESH_COOKIE = "jinguan.refresh";

  private final boolean secure;
  private final Duration ttl;

  public AuthCookies(
      @Value("${jinguan.security.refresh-cookie-secure}") boolean secure,
      @Value("${jinguan.security.jwt-refresh-ttl-days}") long ttlDays) {
    this.secure = secure;
    this.ttl = Duration.ofDays(ttlDays);
  }

  public ResponseCookie refresh(String value) {
    return ResponseCookie.from(REFRESH_COOKIE, value)
        .httpOnly(true)
        .secure(secure)
        .sameSite(Cookie.SameSite.STRICT.attributeValue())
        .path("/auth")
        .maxAge(ttl)
        .build();
  }

  public ResponseCookie clear() {
    return ResponseCookie.from(REFRESH_COOKIE, "")
        .httpOnly(true)
        .secure(secure)
        .sameSite(Cookie.SameSite.STRICT.attributeValue())
        .path("/auth")
        .maxAge(0)
        .build();
  }
}
