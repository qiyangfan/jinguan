package com.longzhong.jinguan.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 租户:tenant_key 创建后不可改,停用是唯一终止语义。 */
@Entity
@Table(name = "tenants")
public class TenantEntity {

  @Id
  @Column(nullable = false, length = 36)
  private String id;

  @Column(name = "tenant_key", nullable = false, unique = true, length = 64)
  private String tenantKey;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA 无参构造。 */
  protected TenantEntity() {}

  static TenantEntity create(String tenantKey, String name) {
    TenantEntity entity = new TenantEntity();
    entity.id = UUID.randomUUID().toString();
    entity.tenantKey = tenantKey;
    entity.name = name;
    entity.status = "ACTIVE";
    entity.createdAt = Instant.now();
    entity.updatedAt = entity.createdAt;
    return entity;
  }

  void rename(String nextName) {
    this.name = nextName;
    this.updatedAt = Instant.now();
  }

  void disable() {
    this.status = "DISABLED";
    this.updatedAt = Instant.now();
  }

  void enable() {
    this.status = "ACTIVE";
    this.updatedAt = Instant.now();
  }

  public boolean isActive() {
    return "ACTIVE".equals(status);
  }

  public String getId() {
    return id;
  }

  public String getTenantKey() {
    return tenantKey;
  }

  public String getName() {
    return name;
  }

  public String getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
