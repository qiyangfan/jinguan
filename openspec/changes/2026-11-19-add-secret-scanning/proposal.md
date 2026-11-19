# 新增 secret 扫描门禁(gitleaks)

## Why

本仓当前对硬编码凭据零拦截:ci.yml 只有后端(mvn verify 全家桶)与前端五门禁,任何分支都能把真实密钥推上去。跨仓质量门禁 handoff 把 P0-1 secret scanning 排在批次 1——改动成本最低、失泄风险最高。

基线实测(gitleaks 8.30.1,全历史 27 commits)已有 10 条命中,全部是 integration 测试里统一的假 JWT 密钥常量 `test-secret-0123456789abcdef-...`(字面量即声明为 test 值,非任何环境凭据);按 handoff「先改成假值,再考虑路径豁免」的口径,用精确字面量 allowlist 收敛并登记原因/负责人/到期日,不做测试目录级豁免。

## What Changes

- 新增 `.gitleaks.toml`:extend 默认规则集,仅保留 handoff 的统一 allowlist(fixtures/docs)+ 一条精确匹配测试假密钥的 regex allowlist。
- 新增 `.pre-commit-config.yaml`:gitleaks v8.30.1 hook,本地提交前拦截(可选启用,不强制)。
- 新增 `.github/workflows/security.yml`:`secrets` job,`push: main` 与 `pull_request` 触发,无路径过滤;checkout fetch-depth 0 扫全历史,泄漏即非 0。
- 分支保护/ruleset 把 `secrets` 设为 required check(需仓管理员授权,本批不自动执行)。

## Capabilities

无产品语义变更(纯 CI/工具链),`.openspec.yaml` 置 `skip_specs: true`,不新增或修改任何产品规格。

## Non-goals

- 不改任何 Java/前端源码与测试代码;witness 值保持原样,只做扫描口径的精确豁免。
- 不引入 husky 双轨(Node 仓二选一,本仓选 pre-commit)。
- 不做 history rewrite 清除历史假密钥(值非真实凭据,重写历史收益不抵风险)。
- 不在本 change 落地 P0-2 依赖漏洞、P0-3 CodeQL、P0-4 SBOM/许可。

## Impact

- CI:新增独立 workflow(不复用按路径裁剪的 ci.yml),合入后需在分支保护中把 `secrets` 设为 required 才能真正阻断。
- 开发:本地可 `pre-commit install` 提前拦截,不装也不影响 CI 门禁。
- 运行时/契约:零改动。
