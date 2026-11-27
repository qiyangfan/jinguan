package com.longzhong.jinguan.entitlement;

import java.util.List;
import org.springframework.stereotype.Component;

/** 套餐/绑定视图组装与明细持久化;与写用例解耦,降低服务类体积。 */
@Component
class EntitlementAssembler {

  private final PlanQuotaRepository quotas;
  private final PlanEntitlementRepository entitlements;

  EntitlementAssembler(PlanQuotaRepository quotas, PlanEntitlementRepository entitlements) {
    this.quotas = quotas;
    this.entitlements = entitlements;
  }

  EntitlementViews.PlanView viewOf(PlanEntity plan) {
    return new EntitlementViews.PlanView(
        plan.getId(),
        plan.getPlanKey(),
        plan.getName(),
        plan.getStatus(),
        plan.getVersion(),
        plan.getUpdatedAt(),
        quotasOf(plan.getId()),
        entitlementKeysOf(plan.getId()));
  }

  List<EntitlementViews.QuotaView> quotasOf(String planId) {
    return quotas.findByPlanIdOrderByDimensionAsc(planId).stream()
        .map(q -> new EntitlementViews.QuotaView(q.getDimension(), q.getUnit(), q.getLimitValue()))
        .toList();
  }

  List<String> entitlementKeysOf(String planId) {
    return entitlements.findByPlanIdOrderByEntitlementKeyAsc(planId).stream()
        .map(PlanEntitlementEntity::getEntitlementKey)
        .toList();
  }

  void persistDetails(
      String planId, List<EntitlementViews.QuotaView> quotaViews, List<String> entitlementKeys) {
    for (EntitlementViews.QuotaView quota : quotaViews) {
      quotas.save(PlanQuotaEntity.of(planId, quota.dimension(), quota.unit(), quota.limit()));
    }
    for (String key : entitlementKeys) {
      entitlements.save(PlanEntitlementEntity.of(planId, key));
    }
  }

  void clearDetails(String planId) {
    quotas.deleteByPlanId(planId);
    entitlements.deleteByPlanId(planId);
  }
}
