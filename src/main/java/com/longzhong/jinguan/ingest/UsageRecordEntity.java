package com.longzhong.jinguan.ingest;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 用量明细(ingest 行镜像 + 归账结果投影):usage_id 全局唯一(= muniu UsageRow.ID), final_outcome 是投影不是资金事实(资金真相在
 * ledger_entries,经 refId="usage:{ID}" 关联)——重投/补账成功时更新投影与 attempts, 账本 append-only 纪律不约束本表。字段为
 * UsageRow PascalCase 的 snake 镜像,漂移即静默丢字段(跨仓契约)。
 */
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

  /** 首次落库工厂。 */
  public static UsageRecordEntity of(IngestUsageRow row, Long userId, String outcome) {
    return new UsageRecordEntity(row, userId, outcome);
  }

  /**
   * 不可变计费事实比对(同 usage ID 重投/恢复前置校验): 镜像字段不一致即在资金写入前报幂等冲突,不覆盖旧内容。
   * 计费事实固定为首次接收值——key、租户、模型、tokens、费用、币种、状态与源时间。
   */
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

  /** 内部结算尝试时间(用户轮转用);包私有——不进入请求契约。 */
  Instant getLastSettlementAttemptAt() {
    return lastSettlementAttemptAt;
  }
}
