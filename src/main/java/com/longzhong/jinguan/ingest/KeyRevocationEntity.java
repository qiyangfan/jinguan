package com.longzhong.jinguan.ingest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 终态吊销记录；唯一 key_id 保证幂等，允许先于绑定创建。 */
@Entity
@Table(name = "key_revocations")
public class KeyRevocationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "key_id", nullable = false, unique = true, length = 64)
  private String keyId;

  @Column(nullable = false)
  private String reason;

  @Column(name = "operator_user_id")
  private Long operatorUserId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected KeyRevocationEntity() {} // JPA

  private KeyRevocationEntity(String keyId, String reason, Long operatorUserId) {
    this.keyId = keyId;
    this.reason = reason;
    this.operatorUserId = operatorUserId;
    this.createdAt = Instant.now();
  }

  public static KeyRevocationEntity of(String keyId, String reason, Long operatorUserId) {
    return new KeyRevocationEntity(keyId, reason, operatorUserId);
  }

  public Long getId() {
    return id;
  }

  public String getKeyId() {
    return keyId;
  }

  public String getReason() {
    return reason;
  }

  public Long getOperatorUserId() {
    return operatorUserId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
