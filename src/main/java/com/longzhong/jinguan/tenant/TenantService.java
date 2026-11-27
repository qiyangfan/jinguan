package com.longzhong.jinguan.tenant;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import com.longzhong.jinguan.common.ConflictException;
import com.longzhong.jinguan.common.NotFoundException;
import com.longzhong.jinguan.controlversion.ControlVersionService;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 租户/成员/粗角色写侧:每次变更递增快照版本并审计。 */
@SuppressWarnings("PMD.UseObjectForClearerAPI") // 写用例参数均为独立必填字段,容器对象只增样板
@Service
public class TenantService {

  private static final String TENANT_TARGET = "tenant";
  private static final String RESULT_SUCCESS = "SUCCESS";
  private static final String MAP_VERSION = "version";

  private final TenantRepository tenants;
  private final TenantMemberRepository members;
  private final TenantQueryService queries;
  private final ControlVersionService versions;
  private final AuditRecorder recorder;

  public TenantService(
      TenantRepository tenants,
      TenantMemberRepository members,
      TenantQueryService queries,
      ControlVersionService versions,
      AuditRecorder recorder) {
    this.tenants = tenants;
    this.members = members;
    this.queries = queries;
    this.versions = versions;
    this.recorder = recorder;
  }

  @Transactional
  public TenantViews.TenantView create(
      String tenantKey, String name, String reason, String actorSubject, String actorRole) {
    TenantValidation.requireReason(reason);
    TenantValidation.requireTenantKey(tenantKey);
    String normalizedName = TenantValidation.requireName(name);
    if (tenants.existsByTenantKey(tenantKey)) {
      throw new ConflictException("tenant_key 已存在: " + tenantKey);
    }
    TenantEntity tenant = tenants.save(TenantEntity.create(tenantKey, normalizedName));
    long version = versions.bump();
    recorder.record(
        AuditActor.user(actorSubject, actorRole),
        AuditActions.TENANT_CREATE,
        TENANT_TARGET,
        tenant.getId(),
        tenantKey,
        reason,
        Map.of("name", normalizedName, MAP_VERSION, version),
        RESULT_SUCCESS);
    return queries.get(tenant.getId());
  }

  @Transactional
  public TenantViews.TenantView rename(
      String id, String name, String reason, String actorSubject, String actorRole) {
    TenantValidation.requireReason(reason);
    TenantEntity tenant = queries.requireTenantEntity(id);
    String normalizedName = TenantValidation.requireName(name);
    tenant.rename(normalizedName);
    tenants.save(tenant);
    long version = versions.bump();
    recorder.record(
        AuditActor.user(actorSubject, actorRole),
        AuditActions.TENANT_UPDATE,
        TENANT_TARGET,
        id,
        tenant.getTenantKey(),
        reason,
        Map.of("name", normalizedName, MAP_VERSION, version),
        RESULT_SUCCESS);
    return queries.get(id);
  }

  @Transactional
  public TenantViews.TenantView changeEnabled(
      String id, boolean enabled, String reason, String actorSubject, String actorRole) {
    TenantValidation.requireReason(reason);
    TenantEntity tenant = queries.requireTenantEntity(id);
    if (enabled == tenant.isActive()) {
      return queries.get(id);
    }
    if (enabled) {
      tenant.enable();
    } else {
      tenant.disable();
    }
    tenants.save(tenant);
    long version = versions.bump();
    recorder.record(
        AuditActor.user(actorSubject, actorRole),
        enabled ? AuditActions.TENANT_ENABLE : AuditActions.TENANT_DISABLE,
        TENANT_TARGET,
        id,
        tenant.getTenantKey(),
        reason,
        Map.of("status", tenant.getStatus(), MAP_VERSION, version),
        RESULT_SUCCESS);
    return queries.get(id);
  }

  @Transactional
  public TenantViews.TenantView addMember(
      String tenantId,
      String subject,
      String role,
      String reason,
      String actorSubject,
      String actorRole) {
    TenantValidation.requireReason(reason);
    TenantEntity tenant = queries.requireTenantEntity(tenantId);
    if (!tenant.isActive()) {
      throw new ConflictException("租户已停用,不能新增成员: " + tenant.getTenantKey());
    }
    TenantValidation.validateSubject(subject);
    String normalizedRole = TenantValidation.requireRole(role);
    if (members.existsByTenantIdAndSubject(tenantId, subject)) {
      throw new ConflictException("成员已存在: " + subject);
    }
    members.save(TenantMemberEntity.create(tenantId, subject, normalizedRole));
    long version = versions.bump();
    recorder.record(
        AuditActor.user(actorSubject, actorRole),
        AuditActions.TENANT_MEMBER_ADD,
        "tenant-member",
        tenantId + "/" + subject,
        tenant.getTenantKey(),
        reason,
        Map.of("subject", subject, "role", normalizedRole, MAP_VERSION, version),
        RESULT_SUCCESS);
    return queries.get(tenantId);
  }

  @Transactional
  public TenantViews.TenantView updateMemberRole(
      String tenantId,
      String subject,
      String role,
      String reason,
      String actorSubject,
      String actorRole) {
    TenantValidation.requireReason(reason);
    TenantEntity tenant = queries.requireTenantEntity(tenantId);
    String normalizedRole = TenantValidation.requireRole(role);
    TenantMemberEntity member = requireMember(tenantId, subject);
    member.changeRole(normalizedRole);
    members.save(member);
    long version = versions.bump();
    recorder.record(
        AuditActor.user(actorSubject, actorRole),
        AuditActions.TENANT_MEMBER_UPDATE,
        "tenant-member",
        tenantId + "/" + subject,
        tenant.getTenantKey(),
        reason,
        Map.of("role", normalizedRole, MAP_VERSION, version),
        RESULT_SUCCESS);
    return queries.get(tenantId);
  }

  @Transactional
  public TenantViews.TenantView changeMemberEnabled(
      String tenantId,
      String subject,
      boolean enabled,
      String reason,
      String actorSubject,
      String actorRole) {
    TenantValidation.requireReason(reason);
    TenantEntity tenant = queries.requireTenantEntity(tenantId);
    TenantMemberEntity member = requireMember(tenantId, subject);
    if (enabled) {
      member.enable();
    } else {
      member.disable();
    }
    members.save(member);
    long version = versions.bump();
    recorder.record(
        AuditActor.user(actorSubject, actorRole),
        AuditActions.TENANT_MEMBER_DISABLE,
        "tenant-member",
        tenantId + "/" + subject,
        tenant.getTenantKey(),
        reason,
        Map.of("status", member.getStatus(), MAP_VERSION, version),
        RESULT_SUCCESS);
    return queries.get(tenantId);
  }

  private TenantMemberEntity requireMember(String tenantId, String subject) {
    return members
        .findByTenantIdAndSubject(tenantId, subject)
        .orElseThrow(() -> new NotFoundException("成员不存在: " + subject));
  }
}
