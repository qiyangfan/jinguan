# Spec Delta

## ADDED Requirements

## Purpose

控制面的多来源用量接收：以单一来源口径接收 muniu 的 LLM 用量与 kongming 的产品事件，按来源幂等聚合，并将健康事件与计费行分离；服务身份认证，避免匿名写入与双计。

## ADDED Requirements

### Requirement: 跨产品用量接收

控制面 SHALL 接收 kongming 与 muniu 的用量事件，带 source/tenant/subject/entitlement 维度；幂等键 SHALL 防重复计费；muniu 既有 UsageRow 契约 SHALL 保持不变；LLM token/费用 SHALL 只由 muniu 上报，kongming SHALL NOT 重复上报。

#### Scenario: kongming 上报

- WHEN kongming 推送产品事件
- THEN 按 source/tenant 聚合，幂等

#### Scenario: muniu 零回归

- WHEN muniu 推送既有 UsageRow
- THEN 行为与变更前一致

#### Scenario: 拒绝双计

- WHEN kongming 上报 LLM token/费用
- THEN 拒绝或丢弃，不进入计费

### Requirement: 用量保留与清理

原始 usage/health 事件 SHALL 保留可配置期；聚合/账单 SHALL 按财务保留；幂等键 SHALL 保留覆盖补发窗口；过期 SHALL 清理/归档并审计；SHALL NOT 清理未入账/未聚合事件；SHALL NOT 保留请求内容/凭据。

#### Scenario: 原始事件过期

- WHEN 原始事件超过保留期且已聚合
- THEN 清理/归档并审计

#### Scenario: 未入账不删

- GIVEN 事件未聚合/未入账
- WHEN 清理 job 运行
- THEN 保留，不丢计费

#### Scenario: 账单保留

- WHEN 原始事件清理
- THEN 聚合/账单仍可查询

### Requirement: 健康事件与审计

健康事件 SHALL 单独接收并更新服务状态；SHALL NOT 混入计费行；接收失败 SHALL 可补发；接收/拒绝 SHALL 审计；接收 SHALL 要求服务身份，匿名 SHALL 拒绝。

#### Scenario: 健康上报

- WHEN 服务推送 health
- THEN 服务注册表更新 last_seen

#### Scenario: 失败补发

- GIVEN 控制面不可用
- WHEN 服务缓存后补发
- THEN 幂等接收，不重复计费

#### Scenario: 匿名拒绝

- WHEN 无服务身份推送 usage/health
- THEN 401/403，不落库
