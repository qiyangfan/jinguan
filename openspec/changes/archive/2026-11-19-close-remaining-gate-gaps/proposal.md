## Why

上一批门禁补齐后仍有九处缺口,其中三处直接关系资金与供应链安全:

1. **领域纪律零机器门禁**:AGENTS 最高级的两条(全库禁 public setter、`ledger_entries` 只追加)
   只靠人审。实测即抓到真实违规——Topology 三个实体各有 6 个 public setter;
   `LedgerEntryRepository` 继承 `JpaRepository`,每个调用方天然拿到 delete。
2. **无 SAST**:代码逻辑缺陷(注入、路径穿越、不安全反序列化)没有静态分析拦截。
3. **无 SBOM 与许可证合规**:Apache-2.0 开源准备的当下,依赖许可证清单与冲突检查都不存在。
4. **容器供应链裸奔**:三处基础镜像用浮动 tag;CI 不构建镜像,镜像漏洞与 Dockerfile 失效都到部署前才暴露。
5. **依赖只报不修**:osv-scanner 发现漏洞后无自动升级通道,Actions 也按 tag 而非 SHA 固定。
6. **脚本与工作流无静态检查**:`scripts/*.sh`、workflow YAML、Dockerfile 均无 lint。
7. **测试只跑 H2**:Flyway 迁移在 PostgreSQL 上仅靠手工 compose 冒烟,类型与约束差异是盲区。
8. **前端覆盖率仅锁 10% 现状**:6 个页面与共享组件零测试。
9. **SpotBugs 全局豁免过宽**:`EI_EXPOSE_REP*` 对所有类生效,等于用豁免遮住问题。

## What Changes

- **领域纪律**:`LedgerEntryRepository` 收窄为 `Repository` + 显式 `save`(编译期删除 UPDATE/DELETE 入口);
  新增 `DisciplineRulesTest` 扫描主源码,禁 public setter、禁账本 DML/@Modifying、禁仓储回退宽接口;
  Topology 三实体改工厂构造。
- **PostgreSQL 验证**:新增 Testcontainers 1.21.4 + `PostgresMigrationTest`,真库应用全量迁移并验证
  账本追加;无 Docker 时自动跳过。
- **SAST**:新增 `codeql.yml`(java-kotlin 手工构建 + javascript-typescript),push/PR/每周定时。
- **SBOM + 许可证**:`cyclonedx-maven-plugin` 2.9.3 在 verify 产出后端 BOM;前端由
  `pnpm licenses --prod` 生成许可证清单与 CycloneDX SBOM;`license-guard.mjs` 白名单门禁,
  双许可组件按「任一允许」判定。CI 上传 SBOM 构件。
- **容器供应链**:Dockerfile 三个基础镜像按 manifest digest 固定;`USER 1001`、健康检查改 exec 形式;
  CI 新增 `container` 作业构建镜像并用 Trivy 0.76.2 扫描 HIGH/CRITICAL(忽略无修复项);
  hadolint 2.14.0 进 lint(唯一豁免 DL3008,理由记录在 `.hadolint.yaml`)。
- **更新自动化**:新增 `dependabot.yml`(maven / npm / github-actions / docker,每周);
  全部工作流 Actions 改为 commit SHA 固定,注释保留版本。
- **脚本/YAML 检查**:CI 新增 `lint` 作业:shellcheck、actionlint 1.7.12、yamllint 1.38.0、hadolint。
- **前端测试**:新增 jsdom + Testing Library 基建;6 个页面与 4 个共享组件共 39 个用例;
  覆盖率 10.07/6.31/5.03/9.83 → 37.77/38.34/28.39/40.18,阈值上调至 36/37/27/39。
- **SpotBugs 收窄**:全局 `EI_EXPOSE_REP*` 豁免改为 Spring 构造器注入注解 + 10 个具名 record,
  其余类照常报告。
- **审计补差(本轮追加)**:
  - 前端生成类型漂移门禁:`pnpm contracts:gen` + `git diff --exit-code`,防 openapi.json 变化后
    `api-types.gen.ts` 未重新生成。
  - 迁移不可变门禁:`scripts/migration-immutability.sh`,相对 PR 基线/push 前提交/工作树检查
    既有迁移是否被修改、删除或重命名(Flyway 校验和失配只在部署时爆,已提前到 CI)。
  - `make check` 与 CI 对等:补 shellcheck/actionlint/yamllint/hadolint 与 contracts 检查。
  - `container` 作业额外产出 Trivy 镜像 CycloneDX SBOM(覆盖基础镜像 OS 包)并上传。
  - GitHub 原生 secret scanning 与 push protection 已启用(公开仓库免费),把密钥拦截提前到 push。
- **二轮深补**:
  - 账本 append-only 的数据库级兜底:Flyway Java 迁移 `V9__Ledger_append_only` 在 PostgreSQL 上
    创建 UPDATE/DELETE 触发器,H2 上按数据库类型跳过;PG 用例断言两种篡改均被拒绝。
  - CI 断言 `PostgresMigrationTest` 真实执行(tests≥3 且 skipped=0),防止无 Docker 时静默跳过。
  - `container` 作业增加启动冒烟:真 PostgreSQL service + 容器启动 + `/actuator/health` UP。
  - JaCoCo 对 `wallet`/`ingest` 核心包单独设线/分支水位,防新增包藏在 BUNDLE 均值下。
  - `app-shell` 用例(管理员/普通用户/登出)加入;前端覆盖率升至 39.83/40.15/30.15/42.33,
    阈值 38/39/29/41。
  - `xmllint` 覆盖 pom/rulesets/settings 等 XML。
  - required checks 扩为 9 个上下文(`lint`/`compliance`/`container`/CodeQL 两作业);
    Dependabot security updates 开启。

被否决的备选:
- 用 ArchUnit 实现两条纪律:ArchUnit 已随 spring-modulith 进入运行时,但仓储收窄在**编译期**
  即封死调用面,强于事后字节码扫描;源码扫描只用于兜底(防回退宽接口/防 DML 字符串)。
- Testcontainers 2.x:模块坐标与包名整体迁移(`org.testcontainers.postgresql`),Spring Boot 4
  未提供版本管理;本批用 1.21.4 稳定线,迁移单独评估。
- cdxgen 生成前端 SBOM:需下载约 97MB 插件二进制,收益仅是 integrity 哈希;
  前端 SBOM 改由 pnpm 许可证清单同源生成并明确记录「无哈希」限制。
- 前端覆盖率一次冲到 80%:本批以页面/共享组件主路径为目标,水位只锁实测值,后续按功能补测上调。
- 容器本地构建验证:镜像拉取带宽约 200KB/s,构建无法在本地一轮完成;由 CI `container` 作业执法。

## Non-goals

- 不改业务行为与契约字段;Topology 实体重构仅替换构造方式。
- 不引入 Playwright E2E、mutation testing、markdownlint/EditorConfig(无实际增量)。
- 不在本批处理 CodeQL/Trivy/actionlint 的 CI 实跑结论——GitHub 账单锁定解除后首跑验证。
