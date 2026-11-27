# Design: 套餐、配额与权益（entitlement-quota）

## Context

现有 kongming billing 套餐/配额在服务内，muniu 只有 key 级配额；控制面没有统一计划/权益模型。服务域配置与权限码不能上收。

## Goals / Non-Goals

**Goals:**
- 计划/配额/权益单一来源。
- 服务可本地执行并 last-known。

**Non-Goals:**
- 不做用量计费实现。
- 不做真实支付。
- 不定义服务内部权限码/工具配置。

## Decisions

1. 计划、配额维度、权益为显式目录；租户绑定产生快照。
2. 能力/权益键来自服务注册；服务只执行，不在锦官定义服务域配置。
3. 权益变更版本化，服务按版本收敛；缺失/非法 fail-closed。
4. kongming billing 的计划/账单/支付/门户归锦官；服务域配置保留本地。

## Risks / Trade-offs

- 配额单位不一致 → 契约冻结维度/单位，双方测试。
- 权益键与服务能力漂移 → 服务注册能力目录 + 契约测试。
- 权益误配 → 审计 + 版本回滚。

## Migration Plan

greenfield 新增表；kongming billing 控制面移除；服务侧后续接入。
