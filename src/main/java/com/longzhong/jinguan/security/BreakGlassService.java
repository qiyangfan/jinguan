package com.longzhong.jinguan.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 运维 break-glass：默认关闭、凭据只从环境变量读取、短时 ADMIN、每次签发落审计。 */
@Service
public class BreakGlassService {

  public static final String SUBJECT = "ops:break-glass";
  private static final Logger LOG = LoggerFactory.getLogger(BreakGlassService.class);

  private final JwtService jwt;
  private final BreakGlassAuditRepository audits;
  private final boolean active;
  private final String tokenHash;
  private final Duration ttl;

  public BreakGlassService(
      JwtService jwt,
      BreakGlassAuditRepository audits,
      @Value("${jinguan.break-glass.enabled:false}") boolean enabled,
      @Value("${jinguan.break-glass.token:}") String token,
      @Value("${jinguan.break-glass.ttl-minutes:15}") long ttlMinutes) {
    this.jwt = jwt;
    this.audits = audits;
    this.active = enabled && token != null && !token.isBlank();
    this.tokenHash = sha256(token == null ? "" : token);
    this.ttl = Duration.ofMinutes(ttlMinutes);
  }

  public boolean enabled() {
    return active;
  }

  /** 凭据正确时签发短时 ADMIN access token；错误凭据与未启用一律拒绝。 */
  @Transactional
  public String issue(String presentedToken, String sourceIp) {
    if (!active
        || presentedToken == null
        || !constantTimeEquals(sha256(presentedToken), tokenHash)) {
      throw new IllegalArgumentException("break-glass 不可用");
    }
    Instant now = Instant.now();
    String access = jwt.issueAccess(SUBJECT, "break-glass", "ADMIN", now, now.plus(ttl));
    audits.save(new BreakGlassAudit(SUBJECT, sourceIp, now, now.plus(ttl)));
    LOG.warn("break-glass issued sourceIp={} expiresAt={}", sourceIp, now.plus(ttl));
    return access;
  }

  private static boolean constantTimeEquals(String left, String right) {
    return MessageDigest.isEqual(
        left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  }

  private static String sha256(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
