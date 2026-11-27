package com.longzhong.jinguan.command;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 跨服务命令:幂等键唯一;状态 PENDING→CLAIMED→SUCCEEDED/FAILED,超时重试或进死信。 */
@Entity
@Table(name = "control_commands")
public class ControlCommandEntity {

  @Id
  @Column(nullable = false, length = 36)
  private String id;

  @Column(name = "service_id", nullable = false, length = 64)
  private String serviceId;

  @Column(nullable = false, length = 64)
  private String action;

  @Column(name = "action_schema_version", nullable = false)
  private int actionSchemaVersion;

  @Column(name = "params_json", nullable = false, columnDefinition = "TEXT")
  private String paramsJson;

  @Column(nullable = false, length = 512)
  private String reason;

  @Column(nullable = false, length = 64)
  private String actor;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
  private String idempotencyKey;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts;

  @Column(name = "claimed_by", length = 128)
  private String claimedBy;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "result_json", columnDefinition = "TEXT")
  private String resultJson;

  @Column(length = 1024)
  private String error;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  /** JPA 无参构造。 */
  protected ControlCommandEntity() {}

  static ControlCommandEntity pending(
      String serviceId,
      String action,
      int actionSchemaVersion,
      String paramsJson,
      String reason,
      String actor,
      String idempotencyKey,
      int maxAttempts) {
    ControlCommandEntity entity = new ControlCommandEntity();
    entity.id = UUID.randomUUID().toString();
    entity.serviceId = serviceId;
    entity.action = action;
    entity.actionSchemaVersion = actionSchemaVersion;
    entity.paramsJson = paramsJson;
    entity.reason = reason;
    entity.actor = actor;
    entity.idempotencyKey = idempotencyKey;
    entity.status = "PENDING";
    entity.attempts = 0;
    entity.maxAttempts = maxAttempts;
    entity.createdAt = Instant.now();
    entity.updatedAt = entity.createdAt;
    return entity;
  }

  void claim(String claimant) {
    this.status = "CLAIMED";
    this.attempts++;
    this.claimedBy = claimant;
    this.claimedAt = Instant.now();
    this.updatedAt = this.claimedAt;
  }

  void markSucceeded(String result) {
    this.status = "SUCCEEDED";
    this.resultJson = result;
    this.finishedAt = Instant.now();
    this.updatedAt = this.finishedAt;
  }

  void markFailed(String failure) {
    this.status = "FAILED";
    this.error = truncate(failure);
    this.finishedAt = Instant.now();
    this.updatedAt = this.finishedAt;
  }

  void markDeadLetter(String failure) {
    this.status = "DEAD_LETTER";
    this.error = truncate(failure);
    this.finishedAt = Instant.now();
    this.updatedAt = this.finishedAt;
  }

  /** 领取超时且未达重试上限:回到 PENDING 等待再次领取。 */
  @SuppressWarnings("PMD.NullAssignment") // 清空 claimedBy/claimedAt 是重试语义的一部分
  void retry() {
    this.status = "PENDING";
    this.claimedBy = null;
    this.claimedAt = null;
    this.updatedAt = Instant.now();
  }

  private static String truncate(String value) {
    if (value == null) {
      return "执行失败";
    }
    return value.substring(0, Math.min(value.length(), 1000));
  }

  public String getId() {
    return id;
  }

  public String getServiceId() {
    return serviceId;
  }

  public String getAction() {
    return action;
  }

  public int getActionSchemaVersion() {
    return actionSchemaVersion;
  }

  public String getParamsJson() {
    return paramsJson;
  }

  public String getReason() {
    return reason;
  }

  public String getActor() {
    return actor;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public String getClaimedBy() {
    return claimedBy;
  }

  public Instant getClaimedAt() {
    return claimedAt;
  }

  public String getResultJson() {
    return resultJson;
  }

  public String getError() {
    return error;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }
}
