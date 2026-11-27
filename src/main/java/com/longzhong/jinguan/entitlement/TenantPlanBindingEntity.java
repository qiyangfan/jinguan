package com.longzhong.jinguan.entitlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 每租户一行当前套餐绑定;解绑保留行(DISABLED)供审计追溯。 */
@Entity
@Table(name = "tenant_plan_bindings")
public class TenantPlanBindingEntity {

  @Id
  @Column(nullable = false, length = 36)
  private String id;

  @Column(name = "tenant_id", nullable = false, unique = true, length = 36)
  private String tenantId;

  @Column(name = "plan_id", nullable = false, length = 36)
  private String planId;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "bound_at", nullable = false)
  private Instant boundAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA 无参构造。 */
  protected TenantPlanBindingEntity() {}

  static TenantPlanBindingEntity bind(String tenantId, String planId) {
    TenantPlanBindingEntity entity = new TenantPlanBindingEntity();
    entity.id = UUID.randomUUID().toString();
    entity.tenantId = tenantId;
    entity.planId = planId;
    entity.status = "ACTIVE";
    entity.boundAt = Instant.now();
    entity.updatedAt = entity.boundAt;
    return entity;
  }

  void rebind(String nextPlanId) {
    this.planId = nextPlanId;
    this.status = "ACTIVE";
    this.boundAt = Instant.now();
    this.updatedAt = this.boundAt;
  }

  void unbind() {
    this.status = "DISABLED";
    this.updatedAt = Instant.now();
  }

  public boolean isActive() {
    return "ACTIVE".equals(status);
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getPlanId() {
    return planId;
  }

  public String getStatus() {
    return status;
  }

  public Instant getBoundAt() {
    return boundAt;
  }
}
