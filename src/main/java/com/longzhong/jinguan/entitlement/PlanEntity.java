package com.longzhong.jinguan.entitlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 套餐:版本化更新,删除仅在未被绑定时允许(停用语义)。 */
@Entity
@Table(name = "plans")
public class PlanEntity {

  @Id
  @Column(nullable = false, length = 36)
  private String id;

  @Column(name = "plan_key", nullable = false, unique = true, length = 64)
  private String planKey;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(nullable = false)
  private int version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA 无参构造。 */
  protected PlanEntity() {}

  static PlanEntity create(String planKey, String name) {
    PlanEntity entity = new PlanEntity();
    entity.id = UUID.randomUUID().toString();
    entity.planKey = planKey;
    entity.name = name;
    entity.status = "ACTIVE";
    entity.version = 1;
    entity.createdAt = Instant.now();
    entity.updatedAt = entity.createdAt;
    return entity;
  }

  void update(String nextName) {
    this.name = nextName;
    this.version++;
    this.updatedAt = Instant.now();
  }

  void disable() {
    this.status = "DISABLED";
    this.version++;
    this.updatedAt = Instant.now();
  }

  public boolean isActive() {
    return "ACTIVE".equals(status);
  }

  public String getId() {
    return id;
  }

  public String getPlanKey() {
    return planKey;
  }

  public String getName() {
    return name;
  }

  public String getStatus() {
    return status;
  }

  public int getVersion() {
    return version;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
