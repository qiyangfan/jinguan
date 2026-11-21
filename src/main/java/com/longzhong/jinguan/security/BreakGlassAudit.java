package com.longzhong.jinguan.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** break-glass 签发审计；只增，不含凭据本身。 */
@Entity
@Table(name = "break_glass_audit")
public class BreakGlassAudit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 64)
  private String subject;

  @Column(name = "source_ip", nullable = false, length = 64)
  private String sourceIp;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  protected BreakGlassAudit() {} // JPA

  BreakGlassAudit(String subject, String sourceIp, Instant issuedAt, Instant expiresAt) {
    this.subject = subject;
    this.sourceIp = sourceIp;
    this.issuedAt = issuedAt;
    this.expiresAt = expiresAt;
  }

  public Long getId() {
    return id;
  }
}
