# 实施任务

门禁类 change(skip_specs),不涉及资金/契约语义,回归判据 = 等价 CI 命令在本仓干净树与非 0 自证两侧都成立。

## 1. 实现

- [x] 1.1 新增 `.gitleaks.toml`:extend 默认规则集;allowlist 仅保留 fixtures/docs 路径 + 一条精确匹配测试假密钥 `test-secret-0123456789abcdef-…` 的 regex(注明原因/负责人/到期日)。
      验证:`gitleaks detect --source . --redact --exit-code 1` 在本仓干净树 EXIT 0。
- [x] 1.2 新增 `.pre-commit-config.yaml`:gitleaks v8.30.1 hook(`pre-commit install` 后本地提交前生效,不强制)。
      验证:`gitleaks git --pre-commit --staged --redact --exit-code 1` 对暂存区泄漏样例 EXIT 1,无泄漏 EXIT 0。
- [x] 1.3 新增 `.github/workflows/security.yml`:`secrets` job,checkout@v7(fetch-depth 0)+ gitleaks 8.30.1 二进制下载,`detect --source . --redact --verbose --exit-code 1`;`timeout-minutes: 10`、`permissions: contents: read`。
      验证:YAML 可解析;Action/工具版本与 handoff §5.0 基准一致(checkout@v7)。
- [x] 1.4 新增 `.gitleaksignore`:首版 tasks.md 负向记录含样例键原文,条目按「提交:文件:规则:行」精确忽略并在文档中脱敏。
      验证:`gitleaks detect --source . --redact --exit-code 1` EXIT 0。

## 2. CI 落点

- [x] 2.1 workflow 触发为 `push: main` + `pull_request`,不加路径过滤(避免安全作业被路径裁剪跳过)。
      验证:`security.yml` 的 `on` 段与 ci.yml 并存且相互独立。
- [ ] 2.2 推送后 GitHub Actions 实跑 `security` / `secrets` job 绿(需 push 授权;本批不提交不推送)。
      验证:CI run 页面 `secrets` 步骤 EXIT 0。
      阻塞(2026-11-23):已随 `ab8d729` 推送并触发;job 未启动即失败,
      annotation="The job was not started because your account is locked due to a billing issue."

## 3. 负向验收(等价 CI 命令)

- [x] 3.1 干净树:`gitleaks detect --source . --redact --exit-code 1` EXIT 0(27 commits 全历史,no leaks found)。
- [x] 3.2 临时 clone 中提交泄漏样例 `aws_access_key_id = AKIAJ7RQ…(已脱敏)`,同一命令 EXIT 1(Finding: aws-access-token, File: leak-probe.txt)。
      注:handoff 示例值 `AKIAIOSFODNN7EXAMPLE` 在 gitleaks 8.30.1 默认白名单内、不触发,故改用非白名单样例键;`detect` 只扫 git 历史、不扫未跟踪文件,故样例须提交(用一次性 clone,不污染本仓历史)。
- [x] 3.3 不含探针提交的一次性 clone 重跑同一命令 EXIT 0(全历史扫描下「移除」等价于回退该提交,单删文件不会清出历史),本仓干净树亦 EXIT 0。

## 4. 分支保护 required

- [x] 4.1 在仓库 ruleset / branch protection 把 `secrets` 设为 required status check(共享设置,需仓管理员授权后执行)。
      验证(2026-11-23):`gh api repos/qiyangfan/jinguan/branches/main/protection` 的
      `required_status_checks.contexts` 已含 `secrets`(共 9 项)。
- [ ] 4.2 required 生效后自证:在 PR 上提交一次泄漏样例,检查合并按钮被 `secrets` 拦截(负向验证后回滚)。
      阻塞(2026-11-23):billing 锁定期间 `secrets` check 无法实跑,负向自证不成立;PR 样例另需推送授权。

## 5. openspec

- [x] 5.1 `openspec validate 2026-11-19-add-secret-scanning --strict` 通过(skip_specs,无 spec delta)。
