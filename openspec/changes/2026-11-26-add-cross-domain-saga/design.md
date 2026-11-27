# Design: 跨域流程 saga/补偿（cross-domain-saga）

## Context

租户开通、换套餐/配额、key 签发、目录上架需要管理域与服务域协作；项目没有分布式事务，手工分步会留下悬挂状态。

## Goals / Non-Goals

**Goals:**
- 跨域流程可追踪、可重试、可补偿。
- 管理域只写管理数据；服务步骤由服务执行。

**Non-Goals:**
- 不做分布式事务/2PC/跨库锁。
- 不做任意 SQL/脚本/配置写。
- 不由管理域代理写服务库/服务配置。

## Decisions

1. 流程状态机：pending/running/succeeded/failed/compensating/compensated；每步幂等键/超时/重试上限。
2. 管理步骤写管理数据；服务步骤发声明式命令，服务校验/执行/写自己的数据。
3. 失败逆序补偿；补偿幂等；补偿失败进入 manual/blocked 并告警。
4. 命令为有限动作枚举；action/params schema/version 来自 X-01，服务端校验；拒绝未知 action 或任意 SQL/脚本/配置写。
5. 控制台展示流程/步骤/状态；全链路审计；敏感值不落库。
6. 服务宕机流程停在 pending/blocked；不得由管理域代写服务配置。
7. 完成流程/关闭后的 blocked 按保留期清理；未关闭 blocked 不删；审计不删；清理审计。

## 示例

- 租户开通：锦官建租户/权益 → 服务快照/命令；服务失败则补偿管理数据。
- 换套餐：锦官写权益版本 → 服务拉快照；失败回滚权益或发补偿命令。
- key 签发：锦官写租户/绑定/吊销期望 → muniu 执行签发/吊销命令并写实际 key 状态。
- 目录上架：锦官写目录/权益 → kongming 提供定义/版本引用；运行前服务校验权益。

## Risks / Trade-offs

- 悬挂流程 → 超时/重试/blocked/告警。
- 补偿不彻底 → 逆序/幂等/manual 兜底；不伪造成功。
- 命令滥用 → 枚举/服务校验/审计。
- 跨域流程不原子 → 明确最终一致和可见状态。

## Migration Plan

greenfield 新增流程表/状态机/控制台；不迁移服务数据。
