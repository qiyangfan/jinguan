# tenant-directory Specification

## Purpose

控制面的租户/成员/粗角色目录：租户生命周期、成员关系、Keycloak subject 与粗角色映射，以及供运行时服务消费的快照；团队/部门与细粒度权限不在此域。

## Requirements

### Requirement: 租户生命周期

ADMIN SHALL 能创建、更新与停用租户；tenant_key SHALL 唯一且创建后不可改；停用 SHALL 阻止新成员与新服务授权；所有变更 SHALL 记录审计。

#### Scenario: 创建租户

- WHEN ADMIN 创建 tenant_key=t1
- THEN 返回 id，快照版本递增

#### Scenario: 停用租户

- GIVEN 租户有成员与服务绑定
- WHEN ADMIN 停用
- THEN 新授权被拒，历史保留，审计记录

### Requirement: Subject 与粗角色映射

成员关系 SHALL 以 Keycloak subject 为唯一身份键；控制面只维护租户成员与粗角色（owner/admin/member）；细粒度权限码、服务内角色、部门/团队 SHALL NOT 由本域维护；SHALL NOT 保存密码或凭据；成员变更 SHALL 输出新快照版本。

#### Scenario: 加入成员

- WHEN ADMIN 将 subject S 加入 t1 并赋粗角色
- THEN 关系落库，快照版本递增

#### Scenario: 服务域权限不越界

- WHEN ADMIN 查看成员记录
- THEN 只有 subject、租户与粗角色，无服务权限码/部门/团队

#### Scenario: 不存凭据

- WHEN 查看成员记录
- THEN 无密码/token
