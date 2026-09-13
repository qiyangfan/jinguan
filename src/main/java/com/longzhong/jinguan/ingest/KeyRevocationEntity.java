package com.longzhong.jinguan.ingest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** key 终态吊销(安全事件,key_id 永久作废):append-only,唯一约束兜底重复吊销幂等; 不设外键——吊销可先于绑定存在(对未绑定 key 预防性吊销)。 */
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

  /** 吊销工厂:操作人取当前 ADMIN(可空,系统态预留)。 */
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
