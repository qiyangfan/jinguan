# credit-spec-rewrite 提案:主规格金额基座回写(μ → credit)+ 账本规格矛盾清理

## Why

金额基座已由 μ(货币百万分之一)整体切换为 credit(1 credit = 本位币 1e-9):迁移 `V6__credit_base.sql`(全表 rename `*_micros→*_credits` + 存量 ×1000)、`PricingService.creditsToDecimal`(scale 9)、前端 `CREDITS_PER_YUAN = 1_000_000_000n` 均已落地;但**主 specs 库未随迁**,规格与实现口径分裂(2026-09-13 聚合复查确认):

- `openspec/specs/pricing-distribution/spec.md`「下发契约端点」:仍写「金额 μ 转去尾零十进制字符串」——实现已是 credit scale 9(`creditsToDecimal`,与 muniu ParsePrice ≤9 位对齐)。
- `openspec/specs/wallet-ledger/spec.md`:Purpose 与「余额即流水之和」场景仍写 `amount_micros`;「管理员手工入账(现状)」要求段仍声明「仅支持加钱、账本行不记录操作人」——与同文件「管理员调账」(debit 端点已落地)和「资金操作审计」(MANUAL_TOPUP/MANUAL_ADJUST 必记 `operator_user_id`)**同文件自相矛盾**,是 harden-money-ops 归档合并时 delta 只 ADDED 未 MODIFIED 旧现状段留下的合并质量欠账。
- `openspec/specs/ingest-metering/spec.md`「归账与幂等」:仍写 `CostMicro≤0` 跳过——实现字段为 `CostCredits`。

另有一处跨仓同因漂移:`docs/design` 与 muniu 仓 ADR-0006 的 credit 迁移均系单边执行,本仓 AGENTS.md 纪律「跨仓契约变更须双方 change 同步,禁止单边漂移」——本次规格回写与 muniu 仓并行立项的 credit-spec-rewrite 互为镜像、互引留痕,补上这步纪律执行。

## What Changes

1. 三个主 capability 的金额口径回写(μ→credit 1e-9,示例数字等比换算、断言行为不变)。
2. wallet-ledger「管理员手工入账(现状)」矛盾清理:标题去「(现状)」,正文改为与「资金操作审计」「管理员调账」一致(双通道加/减、分录必记操作人)。
3. 全仓文档 μ 残留 grep 清单化处理(HANDOFF/design/README),历史验收记录原文不改、加修订注。

## Non-goals

- 不改任何代码(V6 后实现已正确,纯规格/文档回写)。
- 不改已归档 change 文本(历史快照不改写)。
- 不动 muniu 侧文件(muniu 仓自行回写,两 change 互引)。

## Capabilities

### Modified Capabilities

- `pricing-distribution`:下发契约端点金额口径回写(credit,scale 9)。
- `wallet-ledger`:账本金额字段口径回写 + 手工入账矛盾段清理。
- `ingest-metering`:归账跳过条件字段名回写(CostMicro→CostCredits)。

## Impact

- 文件:三个主 specs(经 delta 归档同步)、`docs/`、`README.md` 的 μ 残留。
- 风险:零(纯文本回写;示例金额等比 ×1000,如 10,000,000μ(10 元)→10,000,000,000 credit(10 元),断言行为不变)。
- 跨仓:muniu 仓 credit-spec-rewrite 互引,双方归档后在各自 HANDOFF 留跨仓纪律执行记录。
