# Proposal: 2026-11-26-add-tenant-directory（租户与团队目录（tenant-directory））

## Why

控制面必须拥有跨服务共享的租户/成员/粗角色与身份映射；当前 jinguan 只有 per-subject principals，没有租户/成员目录。团队/部门、细粒度权限码、服务内角色属于服务域配置，不上收。

## What Changes

- 新增租户、成员关系与粗角色（owner/admin/member）映射。
- 提供租户创建/更新/停用与成员管理端点。
- 输出控制快照投影，供服务 last-known 消费。
- 团队/部门仍由 kongming 服务域配置维护；锦官只保存租户/成员/粗角色。

## Non-goals

- 不保存密码/凭据；Keycloak 仍是身份权威。
- 不拥有团队/部门、细粒度权限码、服务内角色。
- 不拥有服务运行时数据。
- 不做跨租户资源共享。

## Capabilities

### New Capabilities

- `tenant-directory`：租户/团队目录、成员关系与身份映射

### Modified Capabilities

无

## Impact

新增控制面表与 API、快照投影、控制台页面；不触及服务运行时库。
