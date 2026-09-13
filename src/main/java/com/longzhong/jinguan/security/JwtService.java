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

/**
 * JWT 签发与验真(HS256);subject=username,claim uid/role/typ 供授权与业务归属。 双 token:access(短 TTL,API 认证)+
 * refresh(长 TTL,仅 /auth/refresh 轮换换发), typ claim 隔离用途——refresh 凭证不可当 Bearer 用,access 不可当 refresh 用。
 *
 * <p>final:构造器校验失败即抛(密钥不合法),杜绝子类化后的 partially-initialized 面。
 */
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

  /** 验真 access:非法/过期/类型不符返回 null(过滤器转为匿名,由授权规则拒)。 */
  public LoginUser verifyAccess(String jwt) {
    return verify(jwt, TYP_ACCESS);
  }

  /** 验真 refresh:仅供 /auth/refresh 轮换;类型不符(如拿 access 冒充)返回 null。 */
  public LoginUser verifyRefresh(String jwt) {
    return verify(jwt, TYP_REFRESH);
  }

  private String issue(long uid, String username, String role, String typ, Duration ttl) {
    Instant now = Instant.now();
    return Jwts.builder()
        .id(UUID.randomUUID().toString()) // jti:同秒签发不重复,并为未来 refresh 复用检测预留锚点
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
