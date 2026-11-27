package com.longzhong.jinguan.entitlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** 套餐权益键:键来自服务注册能力目录,不定义服务内部权限码。 */
@Entity
@Table(name = "plan_entitlements")
public class PlanEntitlementEntity {

  @Id
  @Column(nullable = false, length = 36)
  private String id;

  @Column(name = "plan_id", nullable = false, length = 36)
  private String planId;

  @Column(name = "entitlement_key", nullable = false, length = 128)
  private String entitlementKey;

  /** JPA 无参构造。 */
  protected PlanEntitlementEntity() {}

  static PlanEntitlementEntity of(String planId, String entitlementKey) {
    PlanEntitlementEntity entity = new PlanEntitlementEntity();
    entity.id = UUID.randomUUID().toString();
    entity.planId = planId;
    entity.entitlementKey = entitlementKey;
    return entity;
  }

  public String getPlanId() {
    return planId;
  }

  public String getEntitlementKey() {
    return entitlementKey;
  }
}
