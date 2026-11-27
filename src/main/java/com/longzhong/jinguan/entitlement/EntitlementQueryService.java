package com.longzhong.jinguan.entitlement;

import com.longzhong.jinguan.tenant.TenantQueryService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 套餐/绑定只读视图与租户权益快照。 */
@Service
public class EntitlementQueryService {

  private final PlanRepository plans;
  private final TenantPlanBindingRepository bindings;
  private final TenantQueryService tenants;
  private final EntitlementAssembler assembler;

  public EntitlementQueryService(
      PlanRepository plans,
      TenantPlanBindingRepository bindings,
      TenantQueryService tenants,
      EntitlementAssembler assembler) {
    this.plans = plans;
    this.bindings = bindings;
    this.tenants = tenants;
    this.assembler = assembler;
  }

  @Transactional(readOnly = true)
  public List<EntitlementViews.PlanView> listPlans() {
    return plans.findAll().stream()
        .sorted(java.util.Comparator.comparing(PlanEntity::getPlanKey))
        .map(assembler::viewOf)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<EntitlementViews.BindingView> listBindings() {
    List<EntitlementViews.BindingView> views = new ArrayList<>();
    for (TenantPlanBindingEntity binding : bindings.findAllByOrderByTenantIdAsc()) {
      views.add(bindingView(binding));
    }
    return views;
  }

  /** 租户权益快照:只含生效绑定;配额/权益键随套餐当前版本。 */
  @Transactional(readOnly = true)
  public EntitlementViews.EntitlementSnapshot snapshot() {
    List<EntitlementViews.TenantEntitlement> entries = new ArrayList<>();
    for (TenantPlanBindingEntity binding : bindings.findAllByOrderByTenantIdAsc()) {
      if (!binding.isActive()) {
        continue;
      }
      PlanEntity plan = plans.findById(binding.getPlanId()).orElse(null);
      if (plan == null || !plan.isActive()) {
        continue;
      }
      String tenantKey = tenants.get(binding.getTenantId()).tenantKey();
      entries.add(
          new EntitlementViews.TenantEntitlement(
              tenantKey,
              plan.getPlanKey(),
              binding.getBoundAt(),
              assembler.quotasOf(plan.getId()),
              assembler.entitlementKeysOf(plan.getId())));
    }
    return new EntitlementViews.EntitlementSnapshot(entries.size(), entries);
  }

  EntitlementViews.PlanView viewOf(PlanEntity plan) {
    return assembler.viewOf(plan);
  }

  EntitlementViews.BindingView bindingView(TenantPlanBindingEntity binding) {
    String tenantKey = tenants.get(binding.getTenantId()).tenantKey();
    String planKey =
        plans.findById(binding.getPlanId()).map(PlanEntity::getPlanKey).orElse("unknown");
    return new EntitlementViews.BindingView(
        binding.getTenantId(), tenantKey, planKey, binding.getStatus(), binding.getBoundAt());
  }
}
