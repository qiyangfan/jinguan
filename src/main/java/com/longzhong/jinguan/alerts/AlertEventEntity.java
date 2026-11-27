package com.longzhong.jinguan.alerts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 告警事件:插入后由投递任务异步发送,支持去重、重试与人工确认。 */
@Entity
@Table(name = "alert_events")
public class AlertEventEntity {

  @Id
  @Column(nullable = false, length = 36)
  private String id;

  @Column(nullable = false, length = 16)
  private String severity;

  @Column(nullable = false, length = 64)
  private String source;

  @Column(nullable = false, length = 64)
  private String type;

  @Column(name = "tenant_key", length = 64)
  private String tenantKey;

  @Column(name = "service_id", length = 64)
  private String serviceId;

  @Column(name = "dedup_key", nullable = false, length = 128)
  private String dedupKey;

  @Column(name = "observed_at", nullable = false)
  private Instant observedAt;

  @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
  private String payloadJson;

  @Column(length = 255)
  private String runbook;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "last_error", length = 1024)
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "acked_at")
  private Instant ackedAt;

  @Column(name = "acked_by", length = 64)
  private String ackedBy;

  /** JPA 无参构造。 */
  protected AlertEventEntity() {}

  static AlertEventEntity pending(
      String id,
      String severity,
      String source,
      String type,
      String tenantKey,
      String serviceId,
      String dedupKey,
      Instant observedAt,
      String payloadJson,
      String runbook) {
    AlertEventEntity entity = new AlertEventEntity();
    entity.id = id;
    entity.severity = severity;
    entity.source = source;
    entity.type = type;
    entity.tenantKey = tenantKey;
    entity.serviceId = serviceId;
    entity.dedupKey = dedupKey;
    entity.observedAt = observedAt;
    entity.payloadJson = payloadJson;
    entity.runbook = runbook;
    entity.status = "PENDING";
    entity.attempts = 0;
    entity.createdAt = Instant.now();
    entity.updatedAt = entity.createdAt;
    return entity;
  }

  void markDelivered() {
    status = "DELIVERED";
    deliveredAt = Instant.now();
    updatedAt = deliveredAt;
    attempts++;
  }

  /** 投递失败;达到上限进入 FAILED,否则保持 PENDING 等待下一轮。 */
  void markAttemptFailed(String error, int maxAttempts) {
    attempts++;
    lastError = error == null ? "投递失败" : error.substring(0, Math.min(error.length(), 1000));
    status = attempts >= maxAttempts ? "FAILED" : "PENDING";
    updatedAt = Instant.now();
  }

  void ack(String actor) {
    status = "ACKED";
    ackedAt = Instant.now();
    ackedBy = actor;
    updatedAt = ackedAt;
  }

  void failImmediately(String error) {
    attempts = Math.max(attempts, 1);
    lastError = error.substring(0, Math.min(error.length(), 1000));
    status = "FAILED";
    updatedAt = Instant.now();
  }

  public String getId() {
    return id;
  }

  public String getSeverity() {
    return severity;
  }

  public String getSource() {
    return source;
  }

  public String getType() {
    return type;
  }

  public String getTenantKey() {
    return tenantKey;
  }

  public String getServiceId() {
    return serviceId;
  }

  public String getDedupKey() {
    return dedupKey;
  }

  public Instant getObservedAt() {
    return observedAt;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public String getRunbook() {
    return runbook;
  }

  public String getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getDeliveredAt() {
    return deliveredAt;
  }

  public Instant getAckedAt() {
    return ackedAt;
  }

  public String getAckedBy() {
    return ackedBy;
  }
}
