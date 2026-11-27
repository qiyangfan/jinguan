# Design: 多来源用量接收（ingest-metering）

## Context

现有 ingest-metering 只接收 muniu UsageRow；kongming 有产品事件与本地 LLM 用量；控制面无法统一计费。LLM 用量已经经 muniu 计量，不能让 kongming 再报一遍。

## Goals / Non-Goals

**Goals:**
- 多来源用量统一入口。
- 幂等/补发/可审计。
- 一个指标一个来源，禁止双计。

**Non-Goals:**
- 不改 muniu 行契约。
- 不做实时计费。
- 不让 kongming 上报 LLM token/费用。

## Decisions

1. 保留 muniu UsageRow 契约；新增通用 usage envelope 与 source 字段。
2. 按 source/tenant/subject/entitlement 聚合；幂等键含 source/event_id/metric。
3. 健康事件单独 envelope，不与计费行混用。
4. 服务身份认证；匿名拒绝。
5. 原始事件按保留期清理/归档，聚合/账单按财务保留；幂等键覆盖补发窗口；清理审计。
5. 口径固定：LLM token/费用 -> muniu；产品事件 -> kongming；账单 -> jinguan。

## Risks / Trade-offs

- 多来源幂等冲突 → 幂等键含 source/id。
- 字段漂移 → 契约测试。
- 双计 → 口径校验 + 负向测试。

## Migration Plan

新增表/字段；muniu 既有行为不变；kongming 只接产品事件。
