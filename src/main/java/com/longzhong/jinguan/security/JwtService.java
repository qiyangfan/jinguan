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
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * JWT 签发与验真(HS256);subject=username,claim uid/role 供授权与业务归属。
 *
 * <p>final:构造器校验失败即抛(密钥不合法),杜绝子类化后的 partially-initialized 面。
 */
@Service
public final class JwtService {

  private final SecretKey key;
  private final Duration ttl;

  public JwtService(
      @Value("${jinguan.security.jwt-secret}") String secret,
      @Value("${jinguan.security.jwt-ttl-minutes}") long ttlMinutes) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.ttl = Duration.ofMinutes(ttlMinutes);
  }

  public String issue(long uid, String username, String role) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(username)
        .claim("uid", uid)
        .claim("role", role)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(key)
        .compact();
  }

  /** 验真并返回 Principal;非法/过期返回 null(过滤器转为匿名,由授权规则拒)。 */
  public LoginUser verify(String jwt) {
    try {
      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt).getPayload();
      Number uid = claims.get("uid", Number.class);
      if (uid == null) {
        return null;
      }
      return new LoginUser(uid.longValue(), claims.getSubject(), claims.get("role", String.class));
    } catch (JwtException | IllegalArgumentException e) {
      return null;
    }
  }

  /** 认证主体:实现 Principal 以便 @AuthenticationPrincipal 直接注入控制器。 */
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
