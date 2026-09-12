package com.longzhong.jinguan.ingest;

/**
 * muniu IngestSink 投递行的接收形态。⚠ Go 侧 UsageRow 结构体无 json tag, encoding/json 按导出字段名原样输出(PascalCase)——本
 * record 属性名逐字段 对齐,勿改成 camelCase(跨仓契约,漂移即静默丢字段)。
 */
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
    long CostMicro,
    String Currency,
    Long LatencyFirstTokenMs,
    long LatencyTotalMs,
    String CreatedAt) {

  public boolean isSuccess() {
    return "ok".equals(Status);
  }
}
