# 实施任务

门禁类 change(skip_specs)。判据 = 缺口全部接进门禁 → 基线归零 → 负向必红 → 全量回归绿。

## 1. 依赖漏洞扫描

- [x] 1.1 `security.yml` 新增 `deps` 作业:osv-scanner 2.7.1,SHA256SUMS 校验后 `scan source -r .`,扫描 pom.xml 与 pnpm-lock.yaml。
- [x] 1.2 新增每周一定时任务(cron `17 1 * * 1`):代码不变也能发现新披露漏洞。
- [x] 1.3 基线归零:首扫 32 条(4 严重/16 高/10 中/2 低,11 个包,全部有修复版本);Spring Boot 4.1.1→4.1.2、前端 `pnpm update` 后复扫 "No issues found"。
- [x] 1.4 负向验收:复制 pom 回退到 4.1.1 扫描 exit 1(18 条),删除探针后仓库扫描 exit 0。

## 2. 前端 ESLint 深化

- [x] 2.1 安装 `eslint-plugin-jsx-a11y@6.10.2`、`eslint-plugin-simple-import-sort@14.0.0`;启用 `recommendedTypeChecked` + a11y flat config + import/exports 排序。
- [x] 2.2 修掉 75 条新增违规(排序 34、require-await 12、no-misused-promises 11、no-base-to-string 8、no-floating-promises 5、unsafe-* 4、多余断言 1),`pnpm lint` exit 0。
- [x] 2.3 双仓同步件(`components/ui/**` + 9 个共享标准件)只关排序规则:实测 lint 后 `git status` 无 ui/ 改动,与 kongming 逐字节一致保持。
- [x] 2.4 类型安全修复:`auth-store` JWT 解码改 `Record<string, unknown>`;`toStatusError` 去掉多余 async 与类型断言;测试 mock 类型化为 `Mock<typeof fetch>`。

## 3. 前端覆盖率门禁

- [x] 3.1 接入 `@vitest/coverage-v8@5.1.1`(与 vitest 同版);`test:coverage` 脚本入 `package.json`。
- [x] 3.2 阈值取实测向下取整:语句 9 / 分支 6 / 函数 5 / 行 9(实测 10.07/6.31/5.03/9.83)。
- [x] 3.3 CI 与 `make check` 改用 `test:coverage`;`coverage/` 进 .gitignore/.prettierignore/eslint ignores。
- [x] 3.4 负向验收:临时把行阈值抬到 95,执行 exit 1("Coverage for lines (9.83%) does not meet global threshold (95%)")。

## 4. 迁移破坏性变更检查

- [x] 4.1 `scripts/migration-guard.sh`:去注释后匹配 DROP TABLE / DROP COLUMN / TRUNCATE / DELETE FROM / RENAME / ALTER COLUMN TYPE。
- [x] 4.2 `scripts/migration-guard-baseline.txt`:仅登记 V6 的单位改名(附理由),其余文件零豁免。
- [x] 4.3 正例:现有 8 个迁移 exit 0;负例:/tmp 探针 `V99 DROP COLUMN` exit 1。
- [x] 4.4 接入 CI backend 作业(先于 mvn verify)与 `make check`。

## 5. 分支保护

- [x] 5.1 主分支 required checks = `backend`/`web`/`secrets`/`deps`;`allow_force_pushes=false`、`allow_deletions=false`、`enforce_admins=false`;不启用 PR 强制。
      验证:`gh api .../branches/main/protection` 返回 contexts 四项。

## 6. 回归

- [x] 6.1 `mvn -B verify`:102 测试 0 失败;Spotless/Checkstyle/PMD/CPD/SpotBugs/JaCoCo 全绿(Spring Boot 4.1.2)。
- [x] 6.2 OpenAPI 漂移门禁:`-Popenapi verify` 重导出后 `git diff openapi.json` 为空。
- [x] 6.3 前端:typecheck / lint / lint:css / format:check / test:coverage / build 全部 exit 0。

## 7. openspec

- [x] 7.1 `openspec validate 2026-11-19-close-quality-gate-gaps --strict` 通过(skip_specs)。
