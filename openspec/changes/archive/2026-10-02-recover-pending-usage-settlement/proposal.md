# 补齐可靠接收、欠费留存与充值后结算

## Why

现有规格要求充值后补账，但实际 `IngestService` 对欠费行返回 skippedInsufficient，HTTP 为 200；木牛只判断 2xx，成功后不保存新批次并删除已续传缓冲。锦官有用量明细，却没有后续结算入口，充值只入账并解除停服。因此明细保留，欠费扣账不会自然恢复。

恢复不能直接复用当前重放逻辑：WalletService 先判余额才查 refId，已扣成功后的低余额重投会误报欠费；明细未校验同 ID 的计费载荷，replayed 还能降级或改归属。这些是本闭环必须同时修复的正确性边界。

## What Changes

- 锦官在资金处理前可靠保存用量事实；2xx 表示已可靠接收，结算可以暂缓。
- 复用 usage_records 增加 pending 投影，保存尚未完成的处理；欠费行仍用 skipped_insufficient，恢复器从已落库且已固定归属的待结算行继续扣款。
- 用单实例、有界周期扫描覆盖充值后的补扣、重启与扣账成功后投影更新失败；不依赖木牛重新投递业务欠费行。
- 先裁决 refId 重放/冲突，再检查新扣款余额；保持每用户事务锁、非负余额和账本 append-only。
- 固定首次合法归属与不可变计费事实，已结算投影不降级。
- 用量页显示待结算；保持 ingest 请求字段和既有 IngestResult 计数形态，跨仓镜像提案登记接收与结算责任。

## Capabilities

### Modified Capabilities

- `ingest-metering`：可靠接收、待结算恢复、归属与终态不变量。
- `wallet-ledger`：低余额重放与入账后结算收敛。

## Non-goals

- 收款继续使用 MockPaymentProvider；不接真实 PSP，不新增退款、兑换码或套餐系统。
- 不引消息队列、第二套账本、持久化事件总线或分布式结算。
- 不自动向后补绑定用户追收历史 skipped_unbound 用量；未绑定行继续留存并解释。
- 不改 PascalCase UsageRow、金额单位、价目/拓扑或终态吊销语义。

## Impact

- ingest 明细接收、钱包幂等、内部恢复器、用量结果呈现及相关测试；必要 schema 演进只走新 Flyway 文件。
- 跨仓配对：`../muniu/openspec/changes/recover-pending-usage-settlement/`，登记到双方 HANDOFF 和控制面契约表。
- 依据现有 wallet/ingest 设计、AGENTS.md 账本纪律、Modulith 模块边界；无需新增基础设施依赖。
