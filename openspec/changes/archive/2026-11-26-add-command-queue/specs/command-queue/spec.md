# Spec Delta

## Purpose

控制面的跨服务运行时命令队列：把控制台提交的 reindex、清缓存、取消任务等命令可靠交给服务执行，带幂等、claim/ack、pending/failed、服务认证与审计；本地运行命令不进队列。

## ADDED Requirements

### Requirement: 命令生命周期

控制面 SHALL 接受有限跨服务命令（reindex/cache_purge/task_cancel），记录目标、原因、actor、幂等键与状态；服务 SHALL 以服务身份 claim/执行/ack；控制台 SHALL 显示 pending/acked/failed；本地运行命令 SHALL 由服务管理台直接执行，不受本队列可用性影响。

#### Scenario: 排队命令

- GIVEN 服务宕机
- WHEN ADMIN 提交 reindex
- THEN 命令 pending，服务恢复后执行

#### Scenario: 失败回执

- GIVEN 服务执行失败
- WHEN ack 失败
- THEN 控制台显示 failed 与原因

#### Scenario: 本地命令不受控

- GIVEN 控制面宕机
- WHEN 运维在服务本地台执行 reindex
- THEN 本地执行并审计，不需要控制面队列

### Requirement: 动作契约

每个跨服务命令 SHALL 声明 action 与 action_schema_version；params SHALL 按 X-01 action schema 校验；未知/不支持 action/版本 SHALL 拒绝；SHALL NOT 接受任意 SQL/脚本/配置写。

#### Scenario: 合法动作

- WHEN 提交带合法 action/schema_version 的命令
- THEN 入队并等待服务 claim

#### Scenario: 未知动作

- WHEN 提交未注册 action 或版本
- THEN 400，不落库

#### Scenario: 参数不合法

- WHEN params 不符合 action schema
- THEN 400，不落库

### Requirement: 命令保留与死信

控制面 SHALL 保留 pending/acked/failed 状态到保留期；超时/重试上限 SHALL 进入 dead-letter；dead-letter/结果保留 SHALL 可配置；清理 SHALL 审计；SHALL NOT 删除未 ack 命令；幂等键 SHALL 保留覆盖重试窗口。

#### Scenario: 进入死信

- GIVEN 命令超时/达到重试上限
- WHEN 服务未 ack
- THEN 进入 dead-letter，控制台可见

#### Scenario: 不删未完成

- WHEN 清理 job 运行
- THEN 未 ack/pending 命令不被删除

#### Scenario: 清理审计

- WHEN 清理过期结果/死信
- THEN 记录命令/状态/结果，可追责

### Requirement: 幂等、认证与审计

同幂等键命令 SHALL 只执行一次；重复提交 SHALL 返回既有命令；命令提交/执行/结果 SHALL 审计；命令 SHALL NOT 接受任意 SQL；服务 claim SHALL 认证，匿名 claim SHALL 拒绝。

#### Scenario: 重复提交

- WHEN 同幂等键再次提交
- THEN 返回既有命令，不重复执行

#### Scenario: 拒绝任意 SQL

- WHEN 提交 SQL 命令
- THEN 400，不落库

#### Scenario: 匿名 claim

- GIVEN 无有效服务身份
- WHEN 调用 claim
- THEN 401/403，不返回命令
