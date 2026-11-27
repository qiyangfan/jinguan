# Spec Delta

## Purpose

控制面的服务注册表：记录各服务实例、能力/权益键、版本、服务 admin API 能力与健康心跳，为控制快照投影、命令排队、服务只读视图与控制台诊断提供事实源。

## ADDED Requirements

### Requirement: 服务注册与心跳

服务 SHALL 以服务身份注册实例、能力/权益键、版本与 admin API 能力并周期心跳；控制面 SHALL 记录 last_seen；超时 SHALL 标记 stale；健康状态 SHALL 由 last_seen/心跳推导，SHALL NOT 伪造；注册/心跳 SHALL 可审计；匿名注册/心跳 SHALL 拒绝。

#### Scenario: 注册服务

- WHEN kongming 以服务身份注册实例
- THEN 控制面可见能力/版本/admin API 能力

#### Scenario: 心跳超时

- GIVEN 实例停止心跳
- WHEN 超过窗口
- THEN 标记 stale，不伪造健康

#### Scenario: 匿名注册

- WHEN 无服务身份注册
- THEN 401/403，不落库

### Requirement: 注册边界

服务注册 SHALL NOT 包含服务只读 API endpoint 或凭据；控制面 SHALL NOT 以注册表做服务发现；服务只读 API 地址 SHALL 由锦官部署配置按 service_id 静态映射；缺失映射 SHALL 显示未知，不猜测。

#### Scenario: 注册不含 endpoint

- WHEN 检查注册 payload
- THEN 无服务只读 API endpoint 或凭据

#### Scenario: 缺失地址映射

- GIVEN 锦官未配置 service_id 的 base URL
- WHEN 打开该服务只读页
- THEN 显示未知/不可用，不猜测地址

### Requirement: 注册记录保留

控制面 SHALL 保留 stale 服务记录到配置保留期；SHALL NOT 自动删除活跃/审计记录；清理 SHALL 审计；SHALL NOT 伪造健康或能力。

#### Scenario: stale 清理

- GIVEN 实例 stale 超过保留期
- WHEN 清理 job 运行
- THEN 删除/归档并审计，不影响活跃实例

#### Scenario: 活跃不删

- WHEN 清理 job 运行
- THEN 活跃实例与审计记录保留

#### Scenario: 不伪造

- WHEN 查看已清理/不存在实例
- THEN 显示未知，不伪造健康

### Requirement: 服务能力视图

控制面 SHALL 提供服务能力/版本/健康只读视图；SHALL NOT 因 stale 自动删除服务记录；视图 SHALL NOT 泄露凭证；能力目录 SHALL 作为权益/策略包引用的键空间。

#### Scenario: 能力视图

- WHEN ADMIN 查看服务
- THEN 显示能力/版本/last_seen/admin API 能力

#### Scenario: 不泄露

- WHEN 查看注册信息
- THEN 无 token/密码
