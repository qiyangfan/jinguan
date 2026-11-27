package com.longzhong.jinguan.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 租户成员:subject 为 Keycloak 唯一身份键;只保存粗角色,不复制用户资料。 */
@Entity
@Table(name = "tenant_members")
public class TenantMemberEntity {

  @Id
  @Column(nullable = false, length = 36)
  private String id;

  @Column(name = "tenant_id", nullable = false, length = 36)
  private String tenantId;

  @Column(nullable = false, length = 64)
  private String subject;

  @Column(nullable = false, length = 16)
  private String role;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA 无参构造。 */
  protected TenantMemberEntity() {}

  static TenantMemberEntity create(String tenantId, String subject, String role) {
    TenantMemberEntity entity = new TenantMemberEntity();
    entity.id = UUID.randomUUID().toString();
    entity.tenantId = tenantId;
    entity.subject = subject;
    entity.role = role;
    entity.status = "ACTIVE";
    entity.createdAt = Instant.now();
    entity.updatedAt = entity.createdAt;
    return entity;
  }

  void changeRole(String nextRole) {
    this.role = nextRole;
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

  public String getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getSubject() {
    return subject;
  }

  public String getRole() {
    return role;
  }

  public String getStatus() {
    return status;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
