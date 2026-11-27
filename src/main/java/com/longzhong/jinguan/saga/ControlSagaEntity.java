package com.longzhong.jinguan.saga;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 跨域流程:状态机 pending/running/succeeded/failed/compensating/compensated/blocked。 */
@Entity
@Table(name = "control_sagas")
public class ControlSagaEntity {

  @Id
  @Column(nullable = false, length = 36)
  private String id;

  @Column(name = "saga_type", nullable = false, length = 64)
  private String sagaType;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
  private String idempotencyKey;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "context_json", nullable = false, columnDefinition = "TEXT")
  private String contextJson;

  @Column(length = 512)
  private String reason;

  @Column(length = 64)
  private String actor;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "close_reason", length = 512)
  private String closeReason;

  /** JPA 无参构造。 */
  protected ControlSagaEntity() {}

  static ControlSagaEntity start(
      String sagaType, String idempotencyKey, String contextJson, String reason, String actor) {
    ControlSagaEntity entity = new ControlSagaEntity();
    entity.id = UUID.randomUUID().toString();
    entity.sagaType = sagaType;
    entity.idempotencyKey = idempotencyKey;
    entity.status = "PENDING";
    entity.contextJson = contextJson;
    entity.reason = reason;
    entity.actor = actor;
    entity.createdAt = Instant.now();
    entity.updatedAt = entity.createdAt;
    return entity;
  }

  void transitionTo(String nextStatus) {
    this.status = nextStatus;
    this.updatedAt = Instant.now();
  }

  void updateContext(String nextContextJson) {
    this.contextJson = nextContextJson;
    this.updatedAt = Instant.now();
  }

  void close(String reason) {
    this.closedAt = Instant.now();
    this.closeReason = reason;
    this.updatedAt = this.closedAt;
  }

  public boolean isTerminal() {
    return "SUCCEEDED".equals(status) || "COMPENSATED".equals(status);
  }

  public String getId() {
    return id;
  }

  public String getSagaType() {
    return sagaType;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getStatus() {
    return status;
  }

  public String getContextJson() {
    return contextJson;
  }

  public String getReason() {
    return reason;
  }

  public String getActor() {
    return actor;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public String getCloseReason() {
    return closeReason;
  }
}
