# Spec Delta

## Purpose

控制面的共享策略包下发：只拥有策略包引用、作用域、激活状态与版本；服务域规则体、规则类型与执行仍归服务。策略包快照可缓存、可 fail-closed。

## ADDED Requirements

### Requirement: 共享策略包激活与作用域

控制面 SHALL 维护共享策略包引用、作用域、激活状态与版本；激活/回滚 SHALL 显式确认并审计；回滚 SHALL 恢复上一激活版本；SHALL NOT 存储服务域规则体/规则类型。

#### Scenario: 激活新版本

- WHEN ADMIN 激活策略包 v2
- THEN 快照版本递增，v2 生效

#### Scenario: 回滚

- WHEN ADMIN 回滚到 v1
- THEN v1 重新激活，审计记录

#### Scenario: 规则体不越界

- WHEN ADMIN 查看策略包
- THEN 只有包引用/作用域/版本，无规则体或凭据

### Requirement: 策略快照与 fail-closed

策略包快照 SHALL 带版本/ETag；服务 SHALL 缓存 last-known 并在缺失/非法时对安全类策略 fail-closed；服务 SHALL 在自己的规则目录中解析包引用；策略包 SHALL NOT 含凭据。

#### Scenario: 服务取快照

- WHEN 服务拉取策略包
- THEN 返回版本与包引用，服务本地解析规则

#### Scenario: 非法策略

- GIVEN 快照非法
- WHEN 服务加载
- THEN 安全类策略拒绝，保留 last-known
