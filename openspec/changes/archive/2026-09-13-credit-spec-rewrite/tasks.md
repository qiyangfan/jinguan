# 任务清单:credit-spec-rewrite

> 纪律:纯规格/文档回写,零代码改动;与 muniu 仓 credit-spec-rewrite 互为镜像,归档后双方 HANDOFF 互引留痕。

## 1. 主 specs 回写

- [x] 1.1 `pricing-distribution`「下发契约端点」:金额 μ→credit(1e-9,scale 9);delta 归档同步。
- [x] 1.2 `wallet-ledger`:「append-only 账本」amount_micros→amount_credits;「管理员手工入账(现状)」矛盾清理(标题去现状、正文对齐审计要求、场景金额 credit 口径);Purpose 段 `SUM(amount_micros)` 措辞随归档人工同步(delta 不覆盖 Purpose);delta 归档同步。
- [x] 1.3 `ingest-metering`「归账与幂等」:CostMicro≤0→CostCredits≤0;delta 归档同步。
- [x] 1.4 归档后 `grep -rn "μ\|micro" openspec/specs/` 清零核对。

## 2. 文档残留

- [x] 2.1 `grep -rn "μ\|micro\|Micro" docs/ README.md AGENTS.md` 清单化逐条处理;历史验收记录原文不改、加修订注。
- [x] 2.2 HANDOFF 补验收记录:回写范围、grep 清零证据、与 muniu 仓 credit-spec-rewrite 的互引(跨仓纪律执行留痕)。
