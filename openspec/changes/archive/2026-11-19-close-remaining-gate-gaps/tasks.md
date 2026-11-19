# 实施任务

门禁类 change(skip_specs)。判据 = 缺口全部接进门禁 → 负向必红 → 全量回归绿。

## 1. 领域纪律机器门禁

- [x] 1.1 `LedgerEntryRepository` 由 `JpaRepository` 收窄为 `Repository<LedgerEntryEntity, Long>` + 显式 `save`;delete/deleteAll 编译期不可达。
- [x] 1.2 新增 `DisciplineRulesTest`:禁 public setter、禁账本 DML/`@Modifying`、禁仓储回退宽接口,覆盖全部主源码。
- [x] 1.3 首跑抓到真实违规:Topology 三实体 18 个 public setter;重构为 protected 无参构造 + `static of(...)` 工厂,`TopologyWriter` 改用工厂。
- [x] 1.4 负向验收:植入含 public setter 与 `DELETE FROM ledger_entries` 的探针类 → 两条规则均红(exit 1,报出探针文件名);删除后 `DisciplineRulesTest` 2/2 通过。

## 2. PostgreSQL 真库迁移验证

- [x] 2.1 引入 Testcontainers BOM 1.21.4、`spring-boot-testcontainers`、`postgresql`、`junit-jupiter`。
- [x] 2.2 新增 `PostgresMigrationTest`:断言迁移文件数 = `flyway_schema_history` 成功数、版本从 V1 连续;经 `WalletService` 入账并回读分录。
- [x] 2.3 实测:Podman socket 下 2/2 通过(68.58s),日志确认 8 个迁移在 PostgreSQL 17 上 apply + `ddl-auto=validate` 通过;无 Docker 环境自动跳过。

## 3. CodeQL SAST

- [x] 3.1 新增 `.github/workflows/codeql.yml`:java-kotlin(manual build,项目源码仅编译)+ javascript-typescript。
- [x] 3.2 触发面:push main、PR、每周三定时;权限最小化(contents/actions read,security-events write)。
- [x] 3.3 版本按 commit SHA 固定(codeql-action v4)。CI 实跑待账单解除后首验。

## 4. SBOM 与许可证门禁

- [x] 4.1 后端 `cyclonedx-maven-plugin` 2.9.3 在 verify 产出 `target/bom.json`(compile/runtime,含许可证)。
- [x] 4.2 前端 `pnpm licenses list --prod --json` 生成许可证清单,`scripts/web-sbom.mjs` 生成 CycloneDX 1.6 SBOM(purl,无哈希,限制已记录)。
- [x] 4.3 `scripts/license-guard.mjs`:白名单 + 双许可「任一允许」;未声明许可证视为失败。
- [x] 4.4 实跑通过:后端 129 组件 / 前端 89 包;负向植入 `GPL-3.0-only` 组件 → exit 1 并报出包名。
- [x] 4.5 `make license-check` 接入;CI `compliance` 作业执行同一脚本并上传两个 SBOM 构件。

## 5. 容器供应链

- [x] 5.1 Dockerfile 三个基础镜像按 manifest digest 固定(node:24-alpine / maven:3.9-eclipse-temurin-25 / eclipse-temurin:25-jre)。
- [x] 5.2 hadolint 基线 3 条:DL3066 `USER 1001`、DL3025 healthcheck 改 exec 形式已修;DL3008 在 `.hadolint.yaml` 豁免并注明 apt 版本滚动理由。复跑 exit 0。
- [x] 5.3 CI 新增 `container` 作业:构建镜像 → Trivy 0.76.2(checksums 校验)`HIGH,CRITICAL` + `--ignore-unfixed`,有修复版本即 exit 1。
- [x] 5.4 已知限制:本地镜像拉取带宽约 200KB/s,Dockerfile 构建未在本地完成;由 CI 作业首跑补证。

## 6. 依赖更新自动化与 Actions 固定

- [x] 6.1 新增 `dependabot.yml`:maven、npm(/web)、github-actions、docker,每周一,minor/patch 分组。
- [x] 6.2 全部工作流 action 改为 commit SHA(checkout v7、setup-java v6、setup-node v7、pnpm v6、codeql v4、upload-artifact v5),注释保留版本。

## 7. 脚本 / YAML / 镜像文件静态检查

- [x] 7.1 CI 新增 `lint` 作业:shellcheck、actionlint 1.7.12(checksums 校验)、yamllint 1.38.0、hadolint 2.14.0。
- [x] 7.2 本地全绿:yamllint(workflows + compose)exit 0(仅关闭 `on` 键的 truthy 误报)、actionlint exit 0、shellcheck exit 0、hadolint exit 0。

## 8. 前端组件 / 页面测试与水位上调

- [x] 8.1 基建:jsdom 31、Testing Library(d React/dom/user-event/jest-dom)、`src/test/setup.ts`(matchMedia/ResizeObserver/scrollIntoView 垫片)、`renderWithQuery`。
- [x] 8.2 用例:4 个共享组件 + Login/Payments/Keys/Wallet/Pricing/Topology 六个页面,共 13 文件 39 用例。
- [x] 8.3 覆盖率 10.07/6.31/5.03/9.83 → **37.77/38.34/28.39/40.18**;阈值上调为 36/37/27/39。
- [x] 8.4 负向验收:`--coverage.thresholds.lines=99` → exit 1("Coverage for lines (40.18%) does not meet global threshold (99%)")。
- [x] 8.5 typecheck / lint / lint:css / format:check / test:coverage / build 全绿。

## 9. SpotBugs 豁免收窄

- [x] 9.1 移除全局 `EI_EXPOSE_REP*`;改为 Spring 六类构造器注入注解 + 10 个具名 record。
- [x] 9.2 去除豁免后基线 39 条(REP2 27 / REP 12),收窄后 `spotbugs:check` exit 0,无新增豁免。

## 10. 全量回归与文档

- [x] 10.1 `mvn -B verify`(含 3 个 PostgreSQL 用例):**107 测试全绿**,PMD/Checkstyle 0/SpotBugs 0/CPD 通过/JaCoCo 达标。
- [x] 10.2 `make license-check` 通过;前端五门禁全绿。
- [x] 10.3 `openspec validate 2026-11-19-close-remaining-gate-gaps --strict` 通过。
- [x] 10.4 HANDOFF 回写状态、下一步与验收记录。

## 11. 审计补差(契约漂移 / 迁移不可变 / 本地对等 / 镜像 SBOM / push 保护)
- [x] 11.1 前端生成类型漂移门禁:CI web `pnpm contracts:gen` + `git diff --exit-code -- src/lib/api-types.gen.ts`;本地实跑零 diff。
- [x] 11.2 迁移不可变门禁:`scripts/migration-immutability.sh` 检查基线到 HEAD 及工作树的修改/删除/重命名;CI backend `fetch-depth: 0` + `MIGRATION_BASE`(PR base SHA / push before SHA)。负向:改 V8 后 exit 1,恢复后 exit 0。
- [x] 11.3 `make check` 与 CI 对等:新增 `lint-config`(shellcheck/actionlint/yamllint/hadolint)与 contracts 检查。
- [x] 11.4 `container` 作业额外产出 `trivy-image.cdx.json` 并上传 image-sbom 构件。
- [x] 11.5 GitHub 原生 secret scanning + push protection 已启用并核对(`security_and_analysis` 两项均为 enabled)。
- [x] 11.6 新增/改动脚本与工作流复跑 shellcheck / actionlint / yamllint 全部 exit 0。

## 12. 二轮深补(数据库级 append-only / 静默跳过 / 启动冒烟 / 包级水位 / app-shell / XML)

- [x] 12.1 Flyway Java 迁移 `db.migration.V9__Ledger_append_only`:PostgreSQL 建 UPDATE/DELETE 触发器,H2 按数据库类型跳过;PG 用例断言两种篡改均抛 `DataAccessException`(3/3 通过)。
- [x] 12.2 CI backend 增加 surefire 报告断言:`PostgresMigrationTest` tests≥3 且 skipped/errors/failures 全 0,防无 Docker 静默跳过。
- [x] 12.3 `container` 作业增加启动冒烟:`postgres:17-alpine` service + `docker run` + `/actuator/health` UP。
- [x] 12.4 `xmllint` 纳入 lint 作业与 `make lint-config`(pom/spotbugs/rulesets/maven-settings)。
- [x] 12.5 JaCoCo 增加 PACKAGE 规则:wallet 行 85/分支 55、ingest 行 85/分支 65(实测 91.7/63.9、95.0/76.2)。
- [x] 12.6 新增 `app-shell` 用例(管理员/普通用户/登出),前端 14 文件 42 用例;覆盖率 39.83/40.15/30.15/42.33,阈值 38/39/29/41。
- [x] 12.7 required checks 扩为 9 个上下文(backend/web/secrets/deps/lint/compliance/container/两个 CodeQL analyze),已用 `gh api` 核对。
- [x] 12.8 Dependabot security updates 已启用(`dependabot_security_updates.status=enabled`)。
- [x] 12.9 `make check` 全绿:107 测试(含 PG 3/3)、Checkstyle/PMD/SpotBugs 0、许可证 129/89、前端 42 用例。

## 13. 三轮补差(构建前置条件 / 门禁防伪 / 本地对等 / 类型收紧)

- [x] 13.1 引入 `maven-enforcer-plugin` 3.6.3(validate 阶段):JDK ≥ 25、Maven ≥ 3.9、`dependencyConvergence`、`requireReleaseDeps`;实跑 4 条规则全过。
- [x] 13.2 `scripts/license-guard.mjs` 改为 fail-closed:缺 `components` 数组、非对象输入、后端 < 80 组件或前端 < 50 包均 exit 1(此前空 BOM 会伪装成通过);负向用空清单实测 exit 1。
- [x] 13.3 `make check` 补齐与 CI 对等:新增 `openapi.json` 重新导出 + `git diff --exit-code` 漂移门禁、`pnpm build`。
- [x] 13.4 `make lint-config` 与 CI lint 作业新增 `node --check scripts/*.mjs`(门禁脚本自身无语法检查)。
- [x] 13.5 引入 `knip` 6.45.1,`pnpm lint:deps` 检查未使用/未声明/幽灵二进制依赖;接入 `make check` 与 CI web 作业,实跑 exit 0。
- [x] 13.6 TypeScript 开启 `noUncheckedIndexedAccess`,修正 5 处越界访问(auth-store 载荷、支付测试按钮、拓扑端点回退);typecheck exit 0。
- [x] 13.7 secrets 作业的 gitleaks 下载补 SHA256 校验(此前为 `curl | tar` 直装),与 osv/actionlint/trivy 一致。
- [x] 13.8 PG 用例断言抽为 `scripts/assert-postgres-tests.mjs`,CI 与 `make check` 共用;本地无 Docker 时由 `ALLOW_PG_SKIP=1` 显式放行,否则红;缺报告/被跳过实测 exit 1。
- [x] 13.9 SpotBugs 开启 `includeTests`,与 Checkstyle/PMD 覆盖面对齐;首跑抓出 4 条测试代码问题(2 条 JSON 换行格式串、1 条 root path 空文件名、1 条 `CountDownLatch.await` 返回值丢弃),修复 3 条、按场景豁免 2 条并注记理由;复跑 0 违规。

## 14. PG 关键业务子集(真库语义,非门禁配置)

- [x] 14.1 新增 `PostgresBusinessFlowTest`(Testcontainers 单容器)三条:ingest 顺次重投 + 并发重复投递只扣一次;账本同用户并发不足额裁决 + 跨用户同 `refId` 冲突裁决;支付并发回调只入账一次。
- [x] 14.2 断言落在 PG 真实语义:`uq_ledger_ref` 唯一冲突裁决、`markPaidIfCreated` 条件更新、余额与分录数不重复。
- [x] 14.3 `assert-postgres-tests.mjs` 扩展为同时校验迁移类与业务类(各 ≥3 条),实测 6 个 PG 用例未跳过。
- [x] 14.4 定向实跑 3/3 通过;Spotless/Checkstyle/PMD/CPD/SpotBugs 全绿。
- [x] 14.5 两类共用 `PostgresContainerSupport` 单容器 + `LauncherSessionListener` 会话结束回收:避免 Podman 下同 JVM 第二个容器客户端失效,也避免 `reuseForks=false` 拖慢全量;实测第二类复用仅 1.4s,容器无残留。
- [x] 14.6 `make check` 全绿:110 测试(含 PG 6/6),许可证 129/89,前端 42 用例与构建通过。
