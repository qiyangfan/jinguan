package com.longzhong.jinguan.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** refresh 会话:jti 追踪轮换与重用检测;consumed=已轮换或已吊销。 */
@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {

  @Id
  @Column(length = 64)
  private String jti;

  @Column(name = "subject", nullable = false)
  private String subject;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private boolean consumed;

  @Column(name = "rotated_at")
  private Instant rotatedAt;

  @Column(name = "replaced_jti", length = 64)
  private String replacedJti;

  protected RefreshSession() {} // JPA

  private RefreshSession(String jti, String subject, Instant expiresAt) {
    this.jti = jti;
    this.subject = subject;
    this.expiresAt = expiresAt;
    this.consumed = false;
  }

  /** 记录一枚新签发的 refresh 票。 */
  public static RefreshSession create(String jti, String subject, Instant expiresAt) {
    return new RefreshSession(jti, subject, expiresAt);
  }

  public String getJti() {
    return jti;
  }

  public String getSubject() {
    return subject;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public boolean isConsumed() {
    return consumed;
  }

  public Instant getRotatedAt() {
    return rotatedAt;
  }

  public String getReplacedJti() {
    return replacedJti;
  }

  /** 记录轮换墓碑:时刻与后继票链接,作为宽限窗口内重复提交的重签依据。 */
  public void markRotated(Instant at, String successorJti) {
    this.rotatedAt = at;
    this.replacedJti = successorJti;
  }
}
