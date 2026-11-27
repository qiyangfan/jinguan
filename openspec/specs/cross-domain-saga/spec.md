# cross-domain-saga Specification

## Purpose

管理域跨服务流程编排：用状态机、幂等键、超时/重试与补偿协调管理数据和服务域动作，不引入分布式事务或跨库写。

## Requirements

### Requirement: 流程状态机与幂等

跨域流程 SHALL 以状态机执行 pending/running/succeeded/failed/compensating/compensated；每步 SHALL 幂等并带超时/重试上限；SHALL NOT 使用分布式事务/跨库锁；管理域 SHALL 只写管理数据，服务步骤 SHALL 以声明式命令请求服务执行。

#### Scenario: 正常完成

- WHEN 所有步骤成功
- THEN 流程标 succeeded，步骤/审计可追

#### Scenario: 重复执行

- WHEN 同幂等键再次触发
- THEN 复用既有步骤结果，不重复执行

#### Scenario: 超时

- GIVEN 步骤超时
- WHEN 达到重试上限
- THEN 流程进入 failed，等待补偿

### Requirement: 补偿

步骤失败 SHALL 按逆序补偿已完成步骤；服务域步骤 SHALL 以服务命令补偿并审计；补偿 SHALL 幂等；补偿失败 SHALL 进入 manual/blocked 并告警，SHALL NOT 静默重试或伪造成功。

#### Scenario: 失败补偿

- GIVEN 第 3 步失败
- WHEN 触发补偿
- THEN 第 2/1 步按逆序补偿，流程标 compensated

#### Scenario: 补偿失败

- GIVEN 补偿命令失败
- WHEN 达到重试上限
- THEN 流程 manual/blocked，告警，不伪造成功

### Requirement: 命令边界与写权限

跨域命令 SHALL 为声明式服务动作枚举；命令 SHALL 带 action/params schema/version 并按 X-01 校验；未知 action/版本 SHALL 拒绝；SHALL NOT 包含任意 SQL/脚本/配置写；服务 SHALL 最终校验并写自己的数据；管理域 SHALL NOT 直连/代理写服务库；服务拒绝命令 SHALL 触发补偿。

#### Scenario: 任意配置拒绝

- WHEN 命令包含任意 SQL/配置写
- THEN 拒绝，流程 failed/补偿

#### Scenario: 服务最终裁决

- WHEN 服务拒绝服务动作
- THEN 管理域不重试直写，按补偿处理

#### Scenario: 未知 action

- WHEN 命令 action 或 schema 版本未注册
- THEN 拒绝，流程 failed/补偿

### Requirement: 流程保留与清理

完成流程 SHALL 保留可配置期；blocked/manual SHALL 保留至人工关闭后保留期；清理 SHALL 审计；SHALL NOT 删除未关闭 blocked；SHALL NOT 删除/改写审计；敏感值 SHALL NOT 保留。

#### Scenario: 完成流程清理

- WHEN 完成流程超过保留期
- THEN 清理/归档并审计

#### Scenario: blocked 不自动删

- GIVEN 流程 manual/blocked
- WHEN 清理 job 运行
- THEN 保留，直到人工关闭并过保留期

#### Scenario: 审计不清理

- WHEN 清理流程
- THEN 审计记录仍可追责

### Requirement: 可见性与审计

控制台 SHALL 显示流程/步骤/pending/failed/compensated；管理写、命令、补偿 SHALL 审计；SHALL NOT 存储敏感值；服务宕机时流程 SHALL 停在 pending/blocked，不得由管理域代写服务配置。

#### Scenario: 服务宕机

- GIVEN 服务不可用
- WHEN 流程到达服务步骤
- THEN 停在 pending/blocked，等待服务恢复或补偿

#### Scenario: 审计

- WHEN 流程状态变化
- THEN 记录 actor/命令/步骤/结果，无敏感值
