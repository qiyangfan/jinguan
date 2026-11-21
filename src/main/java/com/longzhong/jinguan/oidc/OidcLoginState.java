package com.longzhong.jinguan.oidc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 一次性登录 state；只保存 state 哈希、PKCE verifier 与 nonce。 */
@Entity
@Table(name = "oidc_login_states")
public class OidcLoginState {

  @Id
  @Column(name = "state_hash", length = 64)
  private String stateHash;

  @Column(nullable = false, length = 128)
  private String nonce;

  @Column(name = "code_verifier", nullable = false, length = 128)
  private String codeVerifier;

  @Column(name = "return_to", length = 512)
  private String returnTo;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  protected OidcLoginState() {} // JPA

  OidcLoginState(
      String stateHash, String nonce, String codeVerifier, String returnTo, Instant expiresAt) {
    this.stateHash = stateHash;
    this.nonce = nonce;
    this.codeVerifier = codeVerifier;
    this.returnTo = returnTo;
    this.expiresAt = expiresAt;
  }

  public String getStateHash() {
    return stateHash;
  }

  public String getNonce() {
    return nonce;
  }

  public String getCodeVerifier() {
    return codeVerifier;
  }

  public String getReturnTo() {
    return returnTo;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getConsumedAt() {
    return consumedAt;
  }
}
