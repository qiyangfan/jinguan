package com.longzhong.jinguan.policy;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import com.longzhong.jinguan.common.ConflictException;
import com.longzhong.jinguan.common.NotFoundException;
import com.longzhong.jinguan.controlversion.ControlVersionService;
import com.longzhong.jinguan.tenant.TenantQueryService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 共享策略包激活/回滚:只引用服务域规则集版本,不存储规则体。 */
@Service
public class PolicyService {

  private static final String TARGET = "policy-package";
  private static final String POLICY_MISSING = "策略包不存在: ";
  private static final String TENANT_SCOPE = "TENANT";

  private final PolicyPackageRepository packages;
  private final TenantQueryService tenants;
  private final PolicyValidation validation;
  private final ControlVersionService versions;
  private final AuditRecorder recorder;

  public PolicyService(
      PolicyPackageRepository packages,
      TenantQueryService tenants,
      PolicyValidation validation,
      ControlVersionService versions,
      AuditRecorder recorder) {
    this.packages = packages;
    this.tenants = tenants;
    this.validation = validation;
    this.versions = versions;
    this.recorder = recorder;
  }

  /** 创建策略包引用;service_id 必须是身份目录中的已知服务。 */
  @SuppressWarnings("PMD.UseObjectForClearerAPI") // 参数均为必填的独立字段,容器对象只增样板
  @Transactional
  public PolicyViews.PolicyPackageView create(
      String packageKey,
      String serviceId,
      String scope,
      String tenantId,
      String initialRulesetVersion,
      String reason,
      String actorSubject,
      String actorRole) {
    validation.requireReason(reason);
    validation.requirePackageKey(packageKey);
    if (packages.existsByPackageKey(packageKey)) {
      throw new ConflictException("策略包已存在: " + packageKey);
    }
    String normalizedService = validation.requireKnownService(serviceId);
    String normalizedScope = validation.normalizeScope(scope);
    ResolvedScope resolved = resolveScope(normalizedScope, tenantId);
    PolicyPackageEntity policy =
        packages.save(
            PolicyPackageEntity.create(
                packageKey, normalizedService, normalizedScope, resolved.tenantId()));
    String initial = validation.normalizeRulesetVersion(initialRulesetVersion);
    if (initial != null) {
      policy.activate(initial);
      packages.save(policy);
    }
    long version = versions.bump();
    recorder.record(
        AuditActor.user(actorSubject, actorRole),
        AuditActions.POLICY_CREATE,
        TARGET,
        policy.getId(),
        resolved.tenantKey(),
        reason,
        Map.of(
            "packageKey", packageKey,
            "serviceId", normalizedService,
            "scope", normalizedScope,
            "rulesetVersion", initial == null ? "" : initial,
            "version", version),
        "SUCCESS");
    return viewOf(policy);
  }

  /** 激活指定规则集版本;同版本重复激活为幂等空操作。 */
  @SuppressWarnings("PMD.UseObjectForClearerAPI") // 参数均为必填的独立字段,容器对象只增样板
  @Transactional
  public PolicyViews.PolicyPackageView activate(
      String id,
      String rulesetVersion,
      String reason,
      boolean confirmed,
      String actorSubject,
      String actorRole) {
    validation.requireReason(reason);
    requireConfirmed(confirmed, "激活策略包必须显式确认");
    PolicyPackageEntity policy = requirePolicy(id);
    String normalized = validation.normalizeRulesetVersion(rulesetVersion);
    if (normalized == null) {
      throw new IllegalArgumentException("rulesetVersion 必填");
    }
    if (normalized.equals(policy.getActiveRulesetVersion())) {
      return viewOf(policy);
    }
    policy.activate(normalized);
    packages.save(policy);
    long version = versions.bump();
    recorder.record(
        AuditActor.user(actorSubject, actorRole),
        AuditActions.POLICY_ACTIVATE,
        TARGET,
        id,
        tenantKeyOf(policy),
        reason,
        Map.of(
            "packageKey", policy.getPackageKey(),
            "rulesetVersion", normalized,
            "version", version),
        "SUCCESS");
    return viewOf(policy);
  }

  /** 回滚到上一激活版本。 */
  @SuppressWarnings("PMD.UseObjectForClearerAPI") // 参数均为必填的独立字段,容器对象只增样板
  @Transactional
  public PolicyViews.PolicyPackageView rollback(
      String id, String reason, boolean confirmed, String actorSubject, String actorRole) {
    validation.requireReason(reason);
    requireConfirmed(confirmed, "回滚策略包必须显式确认");
    PolicyPackageEntity policy = requirePolicy(id);
    if (!policy.canRollback()) {
      throw new ConflictException("策略包没有可回滚的上一版本: " + policy.getPackageKey());
    }
    policy.rollback();
    packages.save(policy);
    long version = versions.bump();
    recorder.record(
        AuditActor.user(actorSubject, actorRole),
        AuditActions.POLICY_ROLLBACK,
        TARGET,
        id,
        tenantKeyOf(policy),
        reason,
        Map.of(
            "packageKey", policy.getPackageKey(),
            "rulesetVersion", policy.getActiveRulesetVersion(),
            "version", version),
        "SUCCESS");
    return viewOf(policy);
  }

  @Transactional(readOnly = true)
  public List<PolicyViews.PolicyPackageView> list() {
    return packages.findAll().stream()
        .sorted(java.util.Comparator.comparing(PolicyPackageEntity::getPackageKey))
        .map(this::viewOf)
        .toList();
  }

  /** 按服务投影:只含已激活的策略包引用。 */
  @Transactional(readOnly = true)
  public PolicyViews.PolicySnapshot snapshotFor(String serviceId) {
    List<PolicyViews.PackageRef> refs = new ArrayList<>();
    for (PolicyPackageEntity policy : packages.findAll()) {
      if (!policy.getServiceId().equals(serviceId) || !policy.hasActiveVersion()) {
        continue;
      }
      refs.add(
          new PolicyViews.PackageRef(
              policy.getPackageKey(),
              policy.getServiceId(),
              policy.getScope(),
              tenantKeyOf(policy),
              policy.getActiveRulesetVersion(),
              policy.getUpdatedAt()));
    }
    refs.sort(java.util.Comparator.comparing(PolicyViews.PackageRef::packageKey));
    return new PolicyViews.PolicySnapshot(refs.size(), refs);
  }

  private ResolvedScope resolveScope(String scope, String tenantId) {
    if (!TENANT_SCOPE.equals(scope)) {
      if (tenantId != null && !tenantId.isBlank()) {
        throw new IllegalArgumentException("GLOBAL 作用域不接受 tenantId");
      }
      return new ResolvedScope(null, null);
    }
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("TENANT 作用域必须提供 tenantId");
    }
    return new ResolvedScope(tenantId, tenants.requireActive(tenantId).tenantKey());
  }

  private PolicyPackageEntity requirePolicy(String id) {
    return packages.findById(id).orElseThrow(() -> new NotFoundException(POLICY_MISSING + id));
  }

  private static void requireConfirmed(boolean confirmed, String message) {
    if (!confirmed) {
      throw new IllegalArgumentException(message);
    }
  }

  private PolicyViews.PolicyPackageView viewOf(PolicyPackageEntity policy) {
    return new PolicyViews.PolicyPackageView(
        policy.getId(),
        policy.getPackageKey(),
        policy.getServiceId(),
        policy.getScope(),
        tenantKeyOf(policy),
        policy.getActiveRulesetVersion(),
        policy.getPreviousRulesetVersion(),
        policy.getActiveVersion(),
        policy.getStatus(),
        policy.getUpdatedAt());
  }

  private String tenantKeyOf(PolicyPackageEntity policy) {
    return policy.getTenantId() == null ? null : tenants.get(policy.getTenantId()).tenantKey();
  }

  private record ResolvedScope(String tenantId, String tenantKey) {}
}
