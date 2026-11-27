# Proposal: 2026-11-26-add-cross-domain-saga（跨域流程 saga/补偿（cross-domain-saga））

## Why

租户开通、换套餐/配额、key 签发、目录上架等流程需要管理域与服务域协作；没有分布式事务，手工分步会留下悬挂状态。

## What Changes

- 新增跨域流程状态机：pending/running/succeeded/failed/compensating/compensated。
- 每步幂等键、超时、重试上限；失败逆序补偿。
- 管理域只写管理数据；服务步骤以声明式服务命令请求，服务执行并写自己的数据。
- 控制台显示流程/步骤/pending/failed/compensated；全链路审计。

## Non-goals

- 不做分布式事务/2PC/跨库锁。
- 不做任意 SQL/脚本/配置写。
- 不由管理域代理写服务配置/服务库。
- 不做自动资金退款/真实支付补偿。

## Capabilities

### New Capabilities

- `cross-domain-saga`：跨域流程状态机、命令与补偿

### Modified Capabilities

无

## Impact

jinguan 跨域流程表/状态机/控制台/审计；服务命令客户端；不写服务库、不共享事务。
