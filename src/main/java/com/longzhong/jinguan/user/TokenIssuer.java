package com.longzhong.jinguan.user;

import com.longzhong.jinguan.security.JwtService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 为已认证 principal 签发本地双 token，并登记 refresh 会话。 */
@Service
public class TokenIssuer {

  /** 本地双 token。 */
  public record TokenPair(String access, String refresh) {}

  private final JwtService jwt;
  private final RefreshTokenService refreshTokens;

  public TokenIssuer(JwtService jwt, RefreshTokenService refreshTokens) {
    this.jwt = jwt;
    this.refreshTokens = refreshTokens;
  }

  @Transactional
  public TokenPair issue(PrincipalEntity principal) {
    String access =
        jwt.issueAccess(principal.getSubject(), principal.getUsername(), principal.getRole());
    JwtService.RefreshTicket refresh =
        jwt.issueRefreshTicket(
            principal.getSubject(), principal.getUsername(), principal.getRole());
    refreshTokens.record(refresh.jti(), principal.getSubject(), refresh.expiresAt());
    return new TokenPair(access, refresh.token());
  }
}
