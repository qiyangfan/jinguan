package com.longzhong.jinguan.saga;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 流程步骤:管理步骤直接执行,服务步骤经声明式命令;补偿同样幂等。 */
@Entity
@Table(name = "saga_steps")
public class SagaStepEntity {

  @Id
  @Column(nullable = false, length = 36)
  private String id;

  @Column(name = "saga_id", nullable = false, length = 36)
  private String sagaId;

  @Column(nullable = false)
  private int seq;

  @Column(nullable = false, length = 64)
  private String name;

  @Column(nullable = false, length = 16)
  private String kind;

  @Column(length = 64)
  private String action;

  @Column(name = "action_schema_version")
  private Integer actionSchemaVersion;

  @Column(name = "params_json", columnDefinition = "TEXT")
  private String paramsJson;

  @Column(name = "command_id", length = 36)
  private String commandId;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(nullable = false)
  private int attempts;

  @Column(length = 1024)
  private String error;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  /** JPA 无参构造。 */
  protected SagaStepEntity() {}

  static SagaStepEntity define(
      String sagaId,
      int seq,
      String name,
      String kind,
      String action,
      Integer actionSchemaVersion,
      String paramsJson) {
    SagaStepEntity entity = new SagaStepEntity();
    entity.id = UUID.randomUUID().toString();
    entity.sagaId = sagaId;
    entity.seq = seq;
    entity.name = name;
    entity.kind = kind;
    entity.action = action;
    entity.actionSchemaVersion = actionSchemaVersion;
    entity.paramsJson = paramsJson;
    entity.status = "PENDING";
    entity.attempts = 0;
    return entity;
  }

  void markRunning() {
    this.status = "RUNNING";
    this.attempts++;
    this.startedAt = Instant.now();
  }

  void attachCommand(String nextCommandId) {
    this.commandId = nextCommandId;
  }

  void markSucceeded() {
    this.status = "SUCCEEDED";
    this.finishedAt = Instant.now();
  }

  void markFailed(String failure) {
    this.status = "FAILED";
    this.error = truncate(failure);
    this.finishedAt = Instant.now();
  }

  void markCompensated() {
    this.status = "COMPENSATED";
    this.finishedAt = Instant.now();
  }

  void markBlocked(String failure) {
    this.status = "BLOCKED";
    this.error = truncate(failure);
    this.finishedAt = Instant.now();
  }

  private static String truncate(String value) {
    if (value == null) {
      return "失败";
    }
    return value.substring(0, Math.min(value.length(), 1000));
  }

  public String getId() {
    return id;
  }

  public String getSagaId() {
    return sagaId;
  }

  public int getSeq() {
    return seq;
  }

  public String getName() {
    return name;
  }

  public String getKind() {
    return kind;
  }

  public String getAction() {
    return action;
  }

  public Integer getActionSchemaVersion() {
    return actionSchemaVersion;
  }

  public String getParamsJson() {
    return paramsJson;
  }

  public String getCommandId() {
    return commandId;
  }

  public String getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public String getError() {
    return error;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }
}
