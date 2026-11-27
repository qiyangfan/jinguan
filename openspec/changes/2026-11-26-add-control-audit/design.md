# Design: 控制面管理审计（control-audit）

## Context

现有 jinguan 只有 break-glass 审计与账本 operator；控制面新域无统一审计。

## Goals / Non-Goals

**Goals:**
- 所有控制写操作可追责。

**Non-Goals:**
- 不做运行时审计。
- 不做外部投递；告警投递归 `control-ha`，本 change 只提供事件源。

## Decisions

1. append-only 控制面审计；禁止 UPDATE/DELETE。
2. 写操作统一拦截/切面记录；summary 脱敏。
3. 查询/导出 ADMIN；在线保留 >=180 天；到期归档/冷存；清理由独立 job 在保留期后执行并审计。
4. 只读策略：普通列表/健康摘要不写审计；敏感服务配置详情查看/导出写审计；审计不含字段值/凭据。

## Risks / Trade-offs

- 审计量 → 索引/归档。
- 敏感值 → 白名单摘要/脱敏测试。

## Migration Plan

新增表。
