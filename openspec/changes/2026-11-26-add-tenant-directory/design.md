# Design: 租户/成员/粗角色目录（tenant-directory）

## Context

jinguan 现有 principals 只按 subject 保存角色/禁用；没有 tenant/成员实体。团队/部门与细粒度权限当前在 kongming，属于服务域配置。

## Goals / Non-Goals

**Goals:**
- 锦官成为租户/成员/粗角色的唯一写者。
- 服务只消费快照，不本地建租户/成员。

**Non-Goals:**
- 不做 Keycloak 用户管理/SSO。
- 不拥有团队/部门、细粒度权限码、服务内角色。
- 不做服务端数据隔离实现。

## Decisions

1. tenant 为显式实体；subject 只做外键映射，不复制用户资料。
2. 粗角色只覆盖控制面/产品入口（owner/admin/member）；服务权限码和服务内角色留在服务。
3. 团队/部门保留 kongming 服务域配置；第二个服务消费时再提升为共享。
4. 停用为唯一终止语义；不删除历史关系。
5. 成员变更输出新快照版本，服务按版本收敛。

## Risks / Trade-offs

- 成员关系错误导致越权 → 审计 + 快照版本 + 服务端 fail-closed。
- 粗角色与细权限混淆 → 边界表与契约测试固定：锦官不存服务权限码。
- Keycloak subject 变化 → 以 subject 为稳定键，不按用户名关联。

## Migration Plan

greenfield 新增表；无服务数据迁移；kongming 本地租户/成员控制改为快照投影。
