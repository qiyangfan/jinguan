package com.longzhong.jinguan.ingest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/** 保存用量行镜像和结算投影；资金事实由 ledger_entries 中的 usage:{ID} 分录记录。 */
@Entity
@Table(name = "usage_records")
public class UsageRecordEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "usage_id", nullable = false, unique = true, length = 64)
  private String usageId;

  @Column(name = "key_id", nullable = false, length = 64)
  private String keyId;

  @Column(name = "tenant_id", length = 64)
  private String tenantId;

  @Column(name = "model_alias", length = 128)
  private String modelAlias;

  @Column(name = "upstream_model", length = 128)
  private String upstreamModel;

  @Column(length = 32)
  private String status;

  @Column(name = "error_class", length = 64)
  private String errorClass;

  @Column(name = "prompt_tokens", nullable = false)
  private long promptTokens;

  @Column(name = "completion_tokens", nullable = false)
  private long completionTokens;

  @Column(name = "reasoning_tokens", nullable = false)
  private long reasoningTokens;

  @Column(name = "cached_tokens", nullable = false)
  private long cachedTokens;

  @Column(name = "cost_credits", nullable = false)
  private long costCredits;

  @Column(length = 8)
  private String currency;

  @Column(name = "source_created_at", length = 64)
  private String sourceCreatedAt;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "final_outcome", nullable = false, length = 32)
  private String finalOutcome;

  @Column(nullable = false)
  private int attempts = 1;

  @Column(name = "last_attempt_at", nullable = false)
  private Instant lastAttemptAt = Instant.now();

  @Column(name = "last_settlement_attempt_at")
  private Instant lastSettlementAttemptAt;

  protected UsageRecordEntity() {} // JPA

  private UsageRecordEntity(IngestUsageRow row, Long userId, String outcome) {
    this.usageId = row.ID();
    this.keyId = row.APIKeyID();
    this.tenantId = row.TenantID();
    this.modelAlias = row.ModelAlias();
    this.upstreamModel = row.UpstreamModel();
    this.status = row.Status();
    this.errorClass = row.ErrorClass();
    this.promptTokens = row.PromptTokens();
    this.completionTokens = row.CompletionTokens();
    this.reasoningTokens = row.ReasoningTokens();
    this.cachedTokens = row.CachedTokens();
    this.costCredits = row.CostCredits();
    this.currency = row.Currency();
    this.sourceCreatedAt = row.CreatedAt();
    this.userId = userId;
    this.finalOutcome = outcome;
  }

  public static UsageRecordEntity of(IngestUsageRow row, Long userId, String outcome) {
    return new UsageRecordEntity(row, userId, outcome);
  }

  /** 比对重投行的不可变计费字段；不一致时由调用方按幂等冲突拒绝。 */
  public boolean billingFactsEqual(IngestUsageRow row) {
    return java.util.Objects.equals(keyId, row.APIKeyID())
        && java.util.Objects.equals(tenantId, row.TenantID())
        && java.util.Objects.equals(modelAlias, row.ModelAlias())
        && java.util.Objects.equals(upstreamModel, row.UpstreamModel())
        && java.util.Objects.equals(status, row.Status())
        && java.util.Objects.equals(errorClass, row.ErrorClass())
        && promptTokens == row.PromptTokens()
        && completionTokens == row.CompletionTokens()
        && reasoningTokens == row.ReasoningTokens()
        && cachedTokens == row.CachedTokens()
        && costCredits == row.CostCredits()
        && java.util.Objects.equals(currency, row.Currency())
        && java.util.Objects.equals(sourceCreatedAt, row.CreatedAt());
  }

  @JsonSerialize(using = ToStringSerializer.class)
  public Long getId() {
    return id;
  }

  public String getUsageId() {
    return usageId;
  }

  public String getKeyId() {
    return keyId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getModelAlias() {
    return modelAlias;
  }

  public String getUpstreamModel() {
    return upstreamModel;
  }

  public String getStatus() {
    return status;
  }

  public String getErrorClass() {
    return errorClass;
  }

  public long getPromptTokens() {
    return promptTokens;
  }

  public long getCompletionTokens() {
    return completionTokens;
  }

  public long getReasoningTokens() {
    return reasoningTokens;
  }

  public long getCachedTokens() {
    return cachedTokens;
  }

  @JsonSerialize(using = ToStringSerializer.class)
  public long getCostCredits() {
    return costCredits;
  }

  public String getCurrency() {
    return currency;
  }

  public String getSourceCreatedAt() {
    return sourceCreatedAt;
  }

  @JsonSerialize(using = ToStringSerializer.class)
  public Long getUserId() {
    return userId;
  }

  public String getFinalOutcome() {
    return finalOutcome;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getLastAttemptAt() {
    return lastAttemptAt;
  }

  /** 内部结算轮转时间，不进入请求契约。 */
  Instant getLastSettlementAttemptAt() {
    return lastSettlementAttemptAt;
  }
}
