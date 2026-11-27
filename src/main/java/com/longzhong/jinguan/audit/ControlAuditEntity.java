package com.longzhong.jinguan.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 控制面审计记录;只增,不提供更新/删除意图方法。 */
@Entity
@Table(name = "control_audit_log")
public class ControlAuditEntity {

  @Id
  @Column(nullable = false, length = 36)
  private String id;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(nullable = false, length = 128)
  private String actor;

  @Column(name = "actor_kind", nullable = false, length = 16)
  private String actorKind;

  @Column(name = "actor_role", length = 32)
  private String actorRole;

  @Column(nullable = false, length = 64)
  private String action;

  @Column(name = "target_type", length = 64)
  private String targetType;

  @Column(name = "target_id", length = 128)
  private String targetId;

  @Column(name = "tenant_key", length = 64)
  private String tenantKey;

  @Column(length = 512)
  private String reason;

  @Column(name = "summary_json", columnDefinition = "TEXT")
  private String summaryJson;

  @Column(nullable = false, length = 16)
  private String result;

  @Column(name = "request_id", length = 64)
  private String requestId;

  /** JPA 无参构造。 */
  protected ControlAuditEntity() {}

  static ControlAuditEntity of(AuditRecord record) {
    ControlAuditEntity entity = new ControlAuditEntity();
    entity.id = record.id();
    entity.occurredAt = record.occurredAt();
    entity.actor = record.actor().subject();
    entity.actorKind = record.actor().kind().name();
    entity.actorRole = record.actor().role();
    entity.action = record.action();
    entity.targetType = record.targetType();
    entity.targetId = record.targetId();
    entity.tenantKey = record.tenantKey();
    entity.reason = record.reason();
    entity.summaryJson = record.summaryJson();
    entity.result = record.result();
    entity.requestId = record.requestId();
    return entity;
  }

  public String getId() {
    return id;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public String getActor() {
    return actor;
  }

  public String getActorKind() {
    return actorKind;
  }

  public String getActorRole() {
    return actorRole;
  }

  public String getAction() {
    return action;
  }

  public String getTargetType() {
    return targetType;
  }

  public String getTargetId() {
    return targetId;
  }

  public String getTenantKey() {
    return tenantKey;
  }

  public String getReason() {
    return reason;
  }

  public String getSummaryJson() {
    return summaryJson;
  }

  public String getResult() {
    return result;
  }

  public String getRequestId() {
    return requestId;
  }
}
