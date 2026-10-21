package com.longzhong.jinguan.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 使用 HS256 签发和验证 access、refresh JWT，并通过 typ 区分用途。 */
@Service
public final class JwtService {

  private static final String TYP_ACCESS = "access";
  private static final String TYP_REFRESH = "refresh";

  private final SecretKey key;
  private final Duration accessTtl;
  private final Duration refreshTtl;

  public JwtService(
      @Value("${jinguan.security.jwt-secret}") String secret,
      @Value("${jinguan.security.jwt-access-ttl-minutes}") long accessTtlMinutes,
      @Value("${jinguan.security.jwt-refresh-ttl-days}") long refreshTtlDays) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
    this.refreshTtl = Duration.ofDays(refreshTtlDays);
  }

  public String issueAccess(long uid, String username, String role) {
    return issue(uid, username, role, TYP_ACCESS, accessTtl);
  }

  public String issueRefresh(long uid, String username, String role) {
    return issue(uid, username, role, TYP_REFRESH, refreshTtl);
  }

  /** 验证 access token；无效时返回 null。 */
  public LoginUser verifyAccess(String jwt) {
    return verify(jwt, TYP_ACCESS);
  }

  /** 验证 refresh token；无效时返回 null。 */
  public LoginUser verifyRefresh(String jwt) {
    return verify(jwt, TYP_REFRESH);
  }

  private String issue(long uid, String username, String role, String typ, Duration ttl) {
    Instant now = Instant.now();
    return Jwts.builder()
        .id(UUID.randomUUID().toString())
        .subject(username)
        .claim("uid", uid)
        .claim("role", role)
        .claim("typ", typ)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(key)
        .compact();
  }

  private LoginUser verify(String jwt, String expectedTyp) {
    try {
      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt).getPayload();
      if (!expectedTyp.equals(claims.get("typ", String.class))) {
        return null;
      }
      Number uid = claims.get("uid", Number.class);
      if (uid == null) {
        return null;
      }
      return new LoginUser(uid.longValue(), claims.getSubject(), claims.get("role", String.class));
    } catch (JwtException | IllegalArgumentException e) {
      return null;
    }
  }

  /** JWT 认证主体。 */
  public record LoginUser(long uid, String username, String role) implements Principal {

    @Override
    public String getName() {
      return username;
    }

    public String authority() {
      return "ROLE_" + role;
    }
  }
}
