package com.longzhong.jinguan.entitlement;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import com.longzhong.jinguan.common.ConflictException;
import com.longzhong.jinguan.common.NotFoundException;
import com.longzhong.jinguan.controlversion.ControlVersionService;
import com.longzhong.jinguan.tenant.TenantQueryService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 套餐/配额/权益目录与租户绑定写侧:变更递增快照版本并审计。 */
@SuppressWarnings("PMD.UseObjectForClearerAPI") // 写用例参数均为独立必填字段,容器对象只增样板
@Service
public class EntitlementService {

  private static final String PLAN_TARGET = "plan";
  private static final String PLAN_MISSING = "套餐不存在: ";
  private static final String MAP_VERSION = "version";
  private static final String MAP_QUOTAS = "quotas";
  private static final String MAP_ENTITLEMENTS = "entitlements";
  private static final String MAP_PLAN_KEY = "planKey";
  private static final String RESULT_SUCCESS = "SUCCESS";

  private final PlanRepository plans;
  private final TenantPlanBindingRepository bindings;
  private final TenantQueryService tenants;
  private final QuotaValidation quotaValidation;
  private final EntitlementKeyValidation keyValidation;
  private final EntitlementAssembler assembler;
  private final EntitlementQueryService queries;
  private final ControlVersionService versions;
  private final AuditRecorder recorder;

  public EntitlementService(
      PlanRepository plans,
      TenantPlanBindingRepository bindings,
      TenantQueryService tenants,
      QuotaValidation quotaValidation,
      EntitlementKeyValidation keyValidation,
      EntitlementAssembler assembler,
      EntitlementQueryService queries,
      ControlVersionService versions,
      AuditRecorder recorder) {
    this.plans = plans;
    this.bindings = bindings;
    this.tenants = tenants;
    this.quotaValidation = quotaValidation;
    this.keyValidation = keyValidation;
    this.assembler = assembler;
    this.queries = queries;
    this.versions = versions;
    this.recorder = recorder;
  }

  @Transactional
  public EntitlementViews.PlanView create(
      String planKey,
      String name,
      List<EntitlementViews.QuotaView> quotaInputs,
      List<String> entitlementKeys,
      String reason,
      String actorSubject,
      String actorRole) {
    EntitlementValidator.requireReason(reason);
    EntitlementValidator.requirePlanKey(planKey);
    if (plans.existsByPlanKey(planKey)) {
      throw new ConflictException("plan_key 已存在: " + planKey);
    }
    String normalizedName = EntitlementValidator.requireName(name);
    List<EntitlementViews.QuotaView> normalizedQuotas =
        quotaValidation.normalizeQuotas(quotaInputs);
    final List<String> normalizedKeys = keyValidation.normalizeEntitlements(entitlementKeys);
    PlanEntity plan = plans.save(PlanEntity.create(planKey, normalizedName));
    assembler.persistDetails(plan.getId(), normalizedQuotas, normalizedKeys);
    long version = versions.bump();
    recorder.record(
        AuditActor.user(actorSubject, actorRole),
        AuditActions.PLAN_CREATE,
        PLAN_TARGET,
        plan.getId(),
        null,
        reason,
        Map.of(
            "planKey",
            planKey,
            "quotas",
            normalizedQuotas.size(),
            "entitlements",
            normalizedKeys.size(),
            "version",
            version),
        RESULT_SUCCESS);
    return queries.viewOf(plan);
  }

  @Transactional
  public EntitlementViews.PlanView update(
      String id,
      String name,
      List<EntitlementViews.QuotaView> quotaInputs,
      List<String> entitlementKeys,
      String reason,
      String actorSubject,
      String actorRole) {
    EntitlementValidator.requireReason(reason);
    PlanEntity plan = requirePlan(id);
    String normalizedName = EntitlementValidator.requireName(name);
    final List<EntitlementViews.QuotaView> normalizedQuotas =
        quotaValidation.normalizeQuotas(quotaInputs);
    final List<String> normalizedKeys = keyValidation.normalizeEntitlements(entitlementKeys);
    plan.update(normalizedName);
    plans.save(plan);
    assembler.clearDetails(id);
    assembler.persistDetails(id, normalizedQuotas, normalizedKeys);
    long version = versions.bump();
    recorder.record(
        AuditActor.user(actorSubject, actorRole),
        AuditActions.PLAN_UPDATE,
        PLAN_TARGET,
        id,
        null,
        reason,
        Map.of(
            MAP_PLAN_KEY, plan.getPlanKey(),
            MAP_QUOTAS, normalizedQuotas.size(),
            MAP_ENTITLEMENTS, normalizedKeys.size(),
            MAP_VERSION, version),
        RESULT_SUCCESS);
    return queries.viewOf(plan);
  }

  /** 删除套餐:被租户绑定时 409;否则停用(保留历史)。 */
  @Transactional
  public EntitlementViews.PlanView delete(
      String id, String reason, String actorSubject, String actorRole) {
    EntitlementValidator.requireReason(reason);
    PlanEntity plan = requirePlan(id);
    if (!bindings.findByPlanIdAndStatus(id, "ACTIVE").isEmpty()) {
      throw new ConflictException("套餐仍被租户绑定,拒绝删除: " + plan.getPlanKey());
    }
    if (!plan.isActive()) {
      return queries.viewOf(plan);
    }
    plan.disable();
    plans.save(plan);
    long version = versions.bump();
    recorder.record(
        AuditActor.user(actorSubject, actorRole),
        AuditActions.PLAN_DELETE,
        PLAN_TARGET,
        id,
        null,
        reason,
        Map.of(MAP_PLAN_KEY, plan.getPlanKey(), MAP_VERSION, version),
        RESULT_SUCCESS);
    return queries.viewOf(plan);
  }

  /** 绑定/换绑套餐:租户须存在且启用,套餐须启用。 */
  @Transactional
  public EntitlementViews.BindingView bind(
      String tenantId, String planKey, String reason, String actorSubject, String actorRole) {
    EntitlementValidator.requireReason(reason);
    var tenant = tenants.requireActive(tenantId);
    PlanEntity plan =
        plans
            .findByPlanKey(planKey)
            .orElseThrow(() -> new NotFoundException(PLAN_MISSING + planKey));
    if (!plan.isActive()) {
      throw new ConflictException("套餐已停用: " + planKey);
    }
    TenantPlanBindingEntity binding =
        bindings
            .findByTenantId(tenantId)
            .map(
                existing -> {
                  existing.rebind(plan.getId());
                  return existing;
                })
            .orElseGet(() -> TenantPlanBindingEntity.bind(tenantId, plan.getId()));
    bindings.save(binding);
    long version = versions.bump();
    recorder.record(
        AuditActor.user(actorSubject, actorRole),
        AuditActions.TENANT_PLAN_BIND,
        "tenant-plan",
        tenantId,
        tenant.tenantKey(),
        reason,
        Map.of(MAP_PLAN_KEY, planKey, MAP_VERSION, version),
        RESULT_SUCCESS);
    return queries.bindingView(binding);
  }

  /** 解绑:无生效绑定 404。 */
  @Transactional
  public EntitlementViews.BindingView unbind(
      String tenantId, String reason, String actorSubject, String actorRole) {
    EntitlementValidator.requireReason(reason);
    var tenant = tenants.get(tenantId);
    TenantPlanBindingEntity binding =
        bindings
            .findByTenantId(tenantId)
            .filter(TenantPlanBindingEntity::isActive)
            .orElseThrow(() -> new NotFoundException("租户无生效套餐绑定: " + tenant.tenantKey()));
    String planKey =
        plans.findById(binding.getPlanId()).map(PlanEntity::getPlanKey).orElse("unknown");
    binding.unbind();
    bindings.save(binding);
    long version = versions.bump();
    recorder.record(
        AuditActor.user(actorSubject, actorRole),
        AuditActions.TENANT_PLAN_UNBIND,
        "tenant-plan",
        tenantId,
        tenant.tenantKey(),
        reason,
        Map.of(MAP_PLAN_KEY, planKey, MAP_VERSION, version),
        RESULT_SUCCESS);
    return queries.bindingView(binding);
  }

  private PlanEntity requirePlan(String id) {
    return plans.findById(id).orElseThrow(() -> new NotFoundException(PLAN_MISSING + id));
  }
}
