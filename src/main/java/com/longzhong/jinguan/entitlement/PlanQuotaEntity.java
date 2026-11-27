package com.longzhong.jinguan.entitlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** 配额维度:单位显式,限值为非负整数。 */
@Entity
@Table(name = "plan_quotas")
public class PlanQuotaEntity {

  @Id
  @Column(nullable = false, length = 36)
  private String id;

  @Column(name = "plan_id", nullable = false, length = 36)
  private String planId;

  @Column(nullable = false, length = 64)
  private String dimension;

  @Column(nullable = false, length = 32)
  private String unit;

  @Column(name = "limit_value", nullable = false)
  private long limitValue;

  /** JPA 无参构造。 */
  protected PlanQuotaEntity() {}

  static PlanQuotaEntity of(String planId, String dimension, String unit, long limitValue) {
    PlanQuotaEntity entity = new PlanQuotaEntity();
    entity.id = UUID.randomUUID().toString();
    entity.planId = planId;
    entity.dimension = dimension;
    entity.unit = unit;
    entity.limitValue = limitValue;
    return entity;
  }

  public String getPlanId() {
    return planId;
  }

  public String getDimension() {
    return dimension;
  }

  public String getUnit() {
    return unit;
  }

  public long getLimitValue() {
    return limitValue;
  }
}
