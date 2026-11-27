package com.longzhong.jinguan.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 共享策略包:只存引用/作用域/激活版本;规则体与规则类型留在服务域。 */
@Entity
@Table(name = "policy_packages")
public class PolicyPackageEntity {

  @Id
  @Column(nullable = false, length = 36)
  private String id;

  @Column(name = "package_key", nullable = false, unique = true, length = 64)
  private String packageKey;

  @Column(name = "service_id", nullable = false, length = 64)
  private String serviceId;

  @Column(nullable = false, length = 16)
  private String scope;

  @Column(name = "tenant_id", length = 36)
  private String tenantId;

  @Column(name = "active_ruleset_version", length = 64)
  private String activeRulesetVersion;

  @Column(name = "previous_ruleset_version", length = 64)
  private String previousRulesetVersion;

  @Column(name = "active_version", nullable = false)
  private int activeVersion;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA 无参构造。 */
  protected PolicyPackageEntity() {}

  static PolicyPackageEntity create(
      String packageKey, String serviceId, String scope, String tenantId) {
    PolicyPackageEntity entity = new PolicyPackageEntity();
    entity.id = UUID.randomUUID().toString();
    entity.packageKey = packageKey;
    entity.serviceId = serviceId;
    entity.scope = scope;
    entity.tenantId = tenantId;
    entity.activeVersion = 0;
    entity.status = "ACTIVE";
    entity.createdAt = Instant.now();
    entity.updatedAt = entity.createdAt;
    return entity;
  }

  void activate(String rulesetVersion) {
    this.previousRulesetVersion = this.activeRulesetVersion;
    this.activeRulesetVersion = rulesetVersion;
    this.activeVersion++;
    this.updatedAt = Instant.now();
  }

  void rollback() {
    String current = this.activeRulesetVersion;
    this.activeRulesetVersion = this.previousRulesetVersion;
    this.previousRulesetVersion = current;
    this.activeVersion++;
    this.updatedAt = Instant.now();
  }

  public boolean hasActiveVersion() {
    return activeRulesetVersion != null;
  }

  public boolean canRollback() {
    return previousRulesetVersion != null;
  }

  public String getId() {
    return id;
  }

  public String getPackageKey() {
    return packageKey;
  }

  public String getServiceId() {
    return serviceId;
  }

  public String getScope() {
    return scope;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getActiveRulesetVersion() {
    return activeRulesetVersion;
  }

  public String getPreviousRulesetVersion() {
    return previousRulesetVersion;
  }

  public int getActiveVersion() {
    return activeVersion;
  }

  public String getStatus() {
    return status;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
