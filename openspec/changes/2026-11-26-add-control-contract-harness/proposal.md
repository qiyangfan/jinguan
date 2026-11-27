# Proposal: 2026-11-26-add-control-contract-harness（跨仓控制契约测试门禁（control-contract-harness））

## Why

快照、命令、用量/健康、服务只读 API/投影与服务注册的契约散落在各 change，tasks 多写“适用时”，没有单一 owner；三仓各自绿不能证明 wire 契约一致。

## What Changes

- 统一维护控制面契约 schema、示例与生成客户端版本。
- 统一维护命令 action 的 params schema/版本，不只命令 envelope。
- 建立跨仓契约测试门禁：快照/命令（含 action）/用量/健康/服务只读 API/投影/服务注册必跑。
- 明确 X-01 owner、失败即红、破坏性变更版本升级与回滚。

## Non-goals

- 不做业务实现。
- 不替代各仓单测/e2e。
- 不做服务域配置契约。
- 不引入共享库/共享事务。

## Capabilities

### New Capabilities

- `control-contract-harness`：跨仓控制契约测试与门禁

### Modified Capabilities

无

## Impact

契约 schema/生成客户端/CI 门禁；不影响运行时热路径；不写业务数据。
