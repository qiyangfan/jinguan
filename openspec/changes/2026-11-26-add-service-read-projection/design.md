# Design: 服务数据只读投影（service-read-projection）

## Context

管理域需要只读展示服务配置/运行时；服务 schema 私有且可独立部署。直连服务库会共享 schema/凭据/迁移，破坏边界。

## Goals / Non-Goals

**Goals:**
- 管理域经服务只读 API/投影读取。
- 服务宕机可读 last-known 并标 stale。
- 无敏感值、无跨库、无代理写。

**Non-Goals:**
- 不做服务配置写。
- 不做跨库 JOIN/CDC。
- 不做反向同步/第二写者。

## Decisions

1. 只读 = 服务只读 API/投影；禁止直连服务 DB/跨库 JOIN/CDC。
2. 投影信封：source/service_id、observed_at、stale、ETag/version、字段白名单。
3. 锦官缓存 last-known；TTL/stale 阈值显式；无投影显示未知。
4. 服务身份/管理员只读认证；敏感字段脱敏；缓存不落凭据。
5. 服务管理台是配置写入口；锦官只读视图不提供写。
6. 服务只读 API base URL 由锦官部署配置按 service_id 静态映射；注册表不含 endpoint，不做服务发现；缺失映射显示未知。
7. 投影设置 stale 阈值与保留期；过期清理/归档并审计；服务恢复重新拉取；不保留敏感值。
8. 敏感详情查看/导出写只读审计；普通列表/健康摘要不写；审计不含字段值/凭据。

## Risks / Trade-offs

- 投影陈旧 → observed_at/stale/TTL；不伪造实时。
- 缓存副本被误当真相源 → 派生数据标记；服务 API 为源。
- API 契约漂移 → 生成客户端/契约测试。

## Migration Plan

greenfield 新增投影缓存/客户端；不迁移服务数据；服务只读 API 由服务侧提供。
