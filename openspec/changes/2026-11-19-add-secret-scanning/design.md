# 设计:secret 扫描门禁

## Context

见 proposal.md Why。关键事实(本机 gitleaks 8.30.1 实测):

- `gitleaks detect --source .` 只扫 git 历史(27 commits / ~1.4 MB),**不扫未跟踪文件**;
- 全历史基线 10 条命中,均为 `src/test/java/**` 里统一的假 JWT 密钥字面量;
- handoff 示例探针 `AKIAIOSFODNN7EXAMPLE` 在默认规则集白名单内,不会触发(实测 exit 0)。

## Goals / Non-Goals

- Goals:泄漏提交即阻断;抑制可审计;不引入规则级全局放行。
- Non-Goals:history rewrite、本地强制 hook、其他 P0 门禁。

## Decisions

1. **值级 allowlist 收敛存量假密钥**。选精确 regex `test-secret-0123456789abcdef-…`,不选 `src/test/**` 路径豁免——路径豁免会把未来真实泄漏挡在视野外;handoff 也禁止规则级全局放行。
2. **全历史扫描(CI `fetch-depth: 0`)**。选它而非只扫 PR 增量:成本可忽略,且能兜住任何合并形态;历史假密钥已由决策 1 收敛。
3. **负向验收改用非白名单样例键 + 一次性 clone**。等价 CI 命令在 `detect` 语义下只认已提交内容,故在 `/tmp` clone 中提交探针,不污染本仓历史。
4. **本地用 pre-commit(非 husky)**。仓库无既有 hook 体系;handoff 要求二选一,pre-commit 为跨语言统一口径。

## Risks / Trade-offs

- 新测试若写入高熵假密钥会被拦 → 处理:先改低熵值,确需保留再值级 allowlist 并登记到期日。
- 历史持续增长拉长扫描 → 当前毫秒级,暂不优化。
- 分支保护未设 required 时只报告不阻断 → 在 tasks 4.x 明确为合入门槛。

## Migration Plan

config/workflow 合并后,在 ruleset 增加 required check `secrets`;误报处置走「先改假值 → 再值级豁免」。
