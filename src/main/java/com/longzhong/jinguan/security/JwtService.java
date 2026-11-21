package com.longzhong.jinguan.security;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/** 以 RS256 非对称签名签发与验证 access、refresh JWT,以 typ 区分用途。 公钥经 JWKS 发布供 RP 验签;对称共享密钥可被任何持密钥方伪造,故弃用。 */
@Service
public final class JwtService {

  private static final String TYP_ACCESS = "access";
  private static final String TYP_REFRESH = "refresh";

  private final JwtEncoder encoder;
  private final JwtDecoder decoder;
  private final String issuer;
  private final String audience;
  private final Duration accessTtl;
  private final Duration refreshTtl;

  public JwtService(
      JwtEncoder encoder,
      JwtDecoder decoder,
      @Value("${jinguan.security.jwt-issuer:http://localhost:8090}") String issuer,
      @Value("${jinguan.security.jwt-audience:jinguan}") String audience,
      @Value("${jinguan.security.jwt-access-ttl-minutes}") long accessTtlMinutes,
      @Value("${jinguan.security.jwt-refresh-ttl-days}") long refreshTtlDays) {
    this.encoder = encoder;
    this.decoder = decoder;
    this.issuer = issuer;
    this.audience = audience;
    this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
    this.refreshTtl = Duration.ofDays(refreshTtlDays);
  }

  public String issueAccess(String subject, String username, String role) {
    return issue(subject, username, role, TYP_ACCESS, accessTtl);
  }

  /** 指定有效期的 access token；break-glass 用短时窗口。 */
  public String issueAccess(
      String subject, String username, String role, Instant issuedAt, Instant expiresAt) {
    return issue(
        subject, username, role, TYP_ACCESS, UUID.randomUUID().toString(), issuedAt, expiresAt);
  }

  public String issueRefresh(String subject, String username, String role) {
    return issueRefreshTicket(subject, username, role).token();
  }

  /** 签发一枚 refresh 票,并返回 jti 与到期时刻供会话落库。 */
  public RefreshTicket issueRefreshTicket(String subject, String username, String role) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(refreshTtl);
    String jti = UUID.randomUUID().toString();
    return new RefreshTicket(
        issue(subject, username, role, TYP_REFRESH, jti, now, expiresAt), jti, expiresAt);
  }

  /** 以原 jti 与到期时刻重签后继票:宽限窗口内重复提交返回同一后继会话。 */
  @SuppressWarnings("PMD.UseObjectForClearerAPI") // 参数均为必需 claims,容器对象无额外语义
  public String reissueRefresh(
      String subject, String username, String role, String jti, Instant expiresAt) {
    return issue(subject, username, role, TYP_REFRESH, jti, Instant.now(), expiresAt);
  }

  /** 验证 access token;无效时返回 null。 */
  public LoginUser verifyAccess(String jwt) {
    return verify(jwt, TYP_ACCESS);
  }

  /** 验证 refresh token;无效时返回 null。 */
  public LoginUser verifyRefresh(String jwt) {
    return verify(jwt, TYP_REFRESH);
  }

  private String issue(String subject, String username, String role, String typ, Duration ttl) {
    Instant now = Instant.now();
    return issue(subject, username, role, typ, UUID.randomUUID().toString(), now, now.plus(ttl));
  }

  private String issue(
      String subject,
      String username,
      String role,
      String typ,
      String jti,
      Instant issuedAt,
      Instant expiresAt) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .id(jti)
            .issuer(issuer)
            .audience(List.of(audience))
            .subject(username)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .subject(subject)
            .claim("username", username)
            .claim("role", role)
            .claim("typ", typ)
            .build();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private LoginUser verify(String jwt, String expectedTyp) {
    try {
      Jwt token = decoder.decode(jwt);
      if (!expectedTyp.equals(token.getClaimAsString("typ"))) {
        return null;
      }
      String subject = token.getSubject();
      if (subject == null || subject.isBlank()) {
        return null;
      }
      return new LoginUser(
          subject,
          token.getClaimAsString("username"),
          token.getClaimAsString("role"),
          token.getId(),
          token.getExpiresAt());
    } catch (JwtException | IllegalArgumentException e) {
      return null;
    }
  }

  /** 新签发的 refresh 票及其会话标识。 */
  public record RefreshTicket(String token, String jti, Instant expiresAt) {}

  /** JWT 认证主体。 */
  public record LoginUser(
      String subject, String username, String role, String jti, Instant expiresAt)
      implements Principal {

    @Override
    public String getName() {
      return username;
    }

    public String authority() {
      return "ROLE_" + role;
    }
  }
}
