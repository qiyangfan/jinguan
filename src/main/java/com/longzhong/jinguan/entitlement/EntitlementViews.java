package com.longzhong.jinguan.entitlement;

import java.time.Instant;
import java.util.List;

/** 套餐/权益视图与租户权益快照。 */
public interface EntitlementViews {

  /** 配额维度视图。 */
  record QuotaView(String dimension, String unit, long limit) {}

  /** 套餐视图。 */
  record PlanView(
      String id,
      String planKey,
      String name,
      String status,
      int version,
      Instant updatedAt,
      List<QuotaView> quotas,
      List<String> entitlementKeys) {

    public PlanView {
      quotas = List.copyOf(quotas);
      entitlementKeys = List.copyOf(entitlementKeys);
    }
  }

  /** 租户套餐绑定视图。 */
  record BindingView(
      String tenantId, String tenantKey, String planKey, String status, Instant boundAt) {}

  /** 租户权益条目。 */
  record TenantEntitlement(
      String tenantKey,
      String planKey,
      Instant boundAt,
      List<QuotaView> quotas,
      List<String> entitlementKeys) {

    public TenantEntitlement {
      quotas = List.copyOf(quotas);
      entitlementKeys = List.copyOf(entitlementKeys);
    }
  }

  /** 租户权益快照。 */
  record EntitlementSnapshot(int tenantCount, List<TenantEntitlement> tenants) {

    public EntitlementSnapshot {
      tenants = List.copyOf(tenants);
    }
  }
}
