package com.longzhong.jinguan.ingest;

/** muniu UsageRow 的接收形态；Go 按字段名输出 PascalCase，属性名必须逐字段一致。 */
public record IngestUsageRow(
    String ID,
    String RequestID,
    String TenantID,
    String APIKeyID,
    String Provider,
    String ModelAlias,
    String UpstreamModel,
    boolean Stream,
    String Status,
    String ErrorClass,
    long PromptTokens,
    long CompletionTokens,
    long ReasoningTokens,
    long CachedTokens,
    boolean Estimated,
    long CostCredits,
    String Currency,
    Long LatencyFirstTokenMs,
    long LatencyTotalMs,
    String CreatedAt) {

  public boolean isSuccess() {
    return "ok".equals(Status);
  }
}
