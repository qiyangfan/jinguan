# Proposal: 2026-11-26-generalize-usage-ingest（跨产品用量接收（ingest-metering））

## Why

现有 ingest 只服务 muniu；控制面需要统一接收 kongming/muniu 用量与健康，才能计费与执行权益，但同一指标只能有一个来源，避免 LLM 双计。

## What Changes

- 扩展 usage/health 接收为多来源，服务身份认证。
- 按来源/租户/subject/权益维度聚合。
- 固定 usage 口径：LLM token/费用只由 muniu 上报；kongming 只报产品事件。
- 幂等、断网补发、审计。

## Non-goals

- 不改 muniu 既有 UsageRow 字段语义。
- 不做服务端计费实现。
- 不做实时流处理。
- 不让 kongming 重复上报 LLM token/费用。

## Capabilities

### New Capabilities

无

### Modified Capabilities

- `ingest-metering`：跨产品用量/健康接收与聚合

## Impact

ingest 端点/模型/聚合；新增 kongming 来源；控制面计费消费。
