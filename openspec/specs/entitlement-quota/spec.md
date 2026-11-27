# entitlement-quota Specification

## Purpose

控制面的套餐/配额/权益目录：定义计划、配额维度、功能权益与租户绑定，并向运行时服务输出版本化快照；能力键来自服务注册，不拥有服务域配置/权限码。

## Requirements

### Requirement: 套餐与权益目录

控制面 SHALL 提供套餐、配额维度、权益与功能开关目录；维度/单位 SHALL 显式定义；套餐 SHALL 可版本化更新；删除被绑定套餐 SHALL 409；权益/能力键 SHALL 来自服务注册，SHALL NOT 在锦官定义服务内部权限码/工具配置。

#### Scenario: 创建套餐

- WHEN ADMIN 创建套餐 pro
- THEN 返回 id 与配额维度

#### Scenario: 删除被绑定套餐

- GIVEN 套餐已绑定租户
- WHEN ADMIN 删除
- THEN 409，套餐保留

#### Scenario: 服务域配置不越界

- WHEN ADMIN 查看权益目录
- THEN 只有能力/权益键与配额，无数据源/RAG/工具/key 配置

### Requirement: 租户权益快照

租户/团队绑定 SHALL 输出带版本的权益快照；服务 SHALL 按快照执行；快照缺失或非法时安全类权益 SHALL fail-closed；变更 SHALL 审计。

#### Scenario: 绑定套餐

- WHEN ADMIN 将 pro 绑定 t1
- THEN 快照版本递增，t1 获得权益

#### Scenario: 非法快照

- GIVEN 服务收到非法权益快照
- WHEN 启动/刷新
- THEN 安全类权益拒绝，保留 last-known
