## Why

质量门禁盘查发现五处缺口:①无依赖漏洞扫描(Spring Boot BOM 与 pnpm 依赖只能人工盯 CVE);
②主分支无分支保护,任何作业可红着合入;③前端 ESLint 只到 recommended 且非类型感知,
async 误用与 a11y 问题不拦;④前端无覆盖率门禁(3 个测试文件、仅 `src/lib`);
⑤Flyway 迁移无破坏性变更检查,账本表误加 `DROP COLUMN` 现有门禁全绿。

首轮实测即证明缺口真实:osv-scanner 扫出 32 个已知漏洞(4 严重/16 高,均已有修复版本)。

被否决的备选:
- OWASP dependency-check:NVD 数据下载重、需 API key,CI 时长与维护成本不划算;osv-scanner 同时覆盖
  Maven pom 传递依赖与 pnpm-lock,单一作业即可。
- Dependabot/Renovate:需要第三方 App 授权,且本仓依赖升级节奏由契约同步主导;先落地"发现了会红",
  升级仍人工评审,定时任务覆盖新披露漏洞。
- squawk(Postgres 迁移 linter):通用规则集会报出大量与本仓无关的锁表/索引建议,需逐条关闭;
  自有守卫只覆盖账本纪律涉及的破坏性语句,判据与 AGENTS 逐条对应。
- 为凑覆盖率补写组件测试:属于测试建设批次,不在门禁缺口范围;本批只设"只升不降"的水位线。

## What Changes

- `.github/workflows/security.yml`:新增 `deps` 作业(osv-scanner 2.7.1,下载后校验 SHA256),
  扫描 `pom.xml`(含传递依赖)与 `web/pnpm-lock.yaml`;新增每周一定时任务。
- 依赖修复:Spring Boot 4.1.1 → 4.1.2(tomcat 11.0.25、jackson 2.21.7/3.1.7);
  前端 `pnpm update` 到语义范围内最新(brace-expansion/fast-uri/source-map-js/zod 等),漏洞 32 → 0。
- `web/eslint.config.js`:`recommendedTypeChecked`(类型感知)+ jsx-a11y + simple-import-sort;
  75 条新增违规全部以改代码消化。双仓逐字节同步的标准件只关闭排序规则(宪法 Article 5)。
- `web/vitest.config.ts` + `@vitest/coverage-v8`:覆盖率阈值按实测值向下取整
  (语句 9/分支 6/函数 5/行 9),CI 与 `make check` 改用 `test:coverage`。
- `scripts/migration-guard.sh` + `scripts/migration-guard-baseline.txt`:拦截新增迁移中的
  DROP TABLE / DROP COLUMN / TRUNCATE / DELETE FROM / RENAME / ALTER COLUMN TYPE;接入 CI backend 与 `make check`。
- GitHub 主分支保护:required checks = `backend`/`web`/`secrets`/`deps`;禁 force push 与分支删除;
  不引入 PR 强制,不改变直接推送流程。

## Non-goals

- 不新增组件/页面测试,不安装 jsdom/testing-library;覆盖率阈值只锁现状,补齐测试后上调。
- 不引入 Dependabot/Renovate、SBOM、E2E、视觉回归。
- 不修改产品行为、接口契约、数据库 schema;迁移守卫不追溯历史文件(基线仅登记 V6 一次性改名)。
- 不放宽任何既有门禁参数(上一批唯一调整 `maximumAsserts` 保持 5)。
