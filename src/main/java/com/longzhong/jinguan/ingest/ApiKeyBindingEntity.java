package com.longzhong.jinguan.ingest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** muniu key → 控制面用户绑定:计量 ingest 按 key 归账扣费的身份映射。 */
@Entity
@Table(name = "api_key_bindings")
public class ApiKeyBindingEntity {

  @Id
  @Column(name = "key_id", nullable = false, length = 64)
  private String keyId; // = muniu UsageRow.APIKeyID

  @Column(name = "subject", nullable = false)
  private String subject;

  @Column(name = "bound_at", nullable = false)
  private Instant boundAt = Instant.now();

  protected ApiKeyBindingEntity() {} // JPA

  public ApiKeyBindingEntity(String keyId, String subject) {
    this.keyId = keyId;
    this.subject = subject;
    this.boundAt = Instant.now();
  }

  public String getKeyId() {
    return keyId;
  }

  public String getSubject() {
    return subject;
  }

  public Instant getBoundAt() {
    return boundAt;
  }
}
