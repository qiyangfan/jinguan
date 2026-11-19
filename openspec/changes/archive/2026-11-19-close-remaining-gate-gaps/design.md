## Context

本批延续 `2026-11-19-close-quality-gate-gaps`,把门禁盘查剩余九项全部接线。原则不变:
每条门禁必须**默认失败**(有负向证据),参数用工具默认,豁免逐条写明理由。

## Decisions

### D1 账本只追加:收窄类型而非事后扫描

`LedgerEntryRepository` 原继承 `JpaRepository`,delete/deleteAll 对所有调用方可见。改为
`Repository<LedgerEntryEntity, Long>` 并显式声明 `save` 与三个查询方法后,UPDATE/DELETE 在
**编译期不可达**。`DisciplineRulesTest` 再做三件事兜底:禁止仓储回退宽接口、禁止 `@Modifying`
出现在提及 LedgerEntry 的文件、禁止 `UPDATE/DELETE ledger_entries` 字符串。
EntityManager/JdbcTemplate 里的原生 SQL 不会被逐方法解析,但账本表名/实体名的 DML 关键字已覆盖(见 R2)。

### D2 public setter:源码扫描 + 真违规重构

扫描规则用去注释后的 `public ... set[A-Z]*(` 正则,首跑抓到 Topology 三实体 18 个 setter。
重构为 `protected` 无参构造(JPA)+ `static of(...)` 工厂,与 wallet 核心域词汇一致。
扫描只覆盖 `src/main/java`;测试夹具不受实体纪律约束。

### D3 真库迁移:Testcontainers 只测迁移与账本主路径

不把全部 106 个测试迁到 PostgreSQL——H2 的 PostgreSQL 模式是现有测试的成本优势,全面迁移属于
独立批次。新增用例只回答两个问题:①8 个迁移能否在 PG 17 上全量应用并让 `ddl-auto=validate`
通过;②账本在真库上能否追加并汇总。`disabledWithoutDocker = true`,本地无 Docker 时跳过,
CI ubuntu-latest 有 Docker,始终执行。

### D4 许可证策略:允许清单 + 双许可任一允许

后端 129 个组件、前端 89 个包。实测出现 Apache-2.0、MIT、ISC、BSD-2/3/4、CC0、EPL-1.0/2.0、
MPL-2.0、LGPL-2.1-only、GPL-2.0-with-classpath-exception。判据:
- 组件声明多个许可证时,任一在白名单即通过(ArchUnit = Apache-2.0 + BSD;Logback = EPL/LGPL;
  Jakarta API = EPL-2.0 + GPL2+CPE)。
- 无许可证声明视为失败。
- 白名单只收宽松/弱 copyleft 许可证;GPL 全量、AGPL、SSPL 不在列。

前端 SBOM 由 `pnpm licenses --prod --json` 同源生成(组件名、版本、purl),不含 integrity 哈希;
这是与 cdxgen 的取舍:97MB 插件换哈希不值得,许可证门禁与 SBOM 组件清单保持同源更重要。

### D5 容器:digest 固定 + 扫描只拦可修复漏洞

- 三个基础镜像按 manifest list digest 固定,multi-arch 对 amd64/arm64 都生效;Dependabot docker
  ecosystem 负责轮换 digest。
- Trivy 扫描 `HIGH,CRITICAL` 且 `--ignore-unfixed`:基础镜像的未修复漏洞不阻塞,有修复版本却
  未升级必须处理。这是「可持续的红」与「永远红的噪音」之间的取舍。
- hadolint 只豁免 DL3008(apt 版本固定会与 Ubuntu 安全更新冲突),DL3066/DL3025 均以改 Dockerfile
  消化(`USER 1001`、exec 形式 HEALTHCHECK)。

### D6 Actions 按 SHA 固定 + Dependabot 维护

Actions tag 可被上游强制移动,与「篡改即事故」的供应链假设冲突。全部 4 个 action + CodeQL
action 改为 commit SHA,注释保留人类可读版本;Dependabot 的 github-actions ecosystem 会在
保留 SHA 的同时升级版本。

### D7 SpotBugs 豁免按注解与具名类限定

原全局 `EI_EXPOSE_REP*` 改为:
- `EI_EXPOSE_REP2` 仅匹配 `@Component/@Service/@Repository/@Controller/@RestController/@Configuration`
  ——Spring 容器注入协作者的框架范式误报;
- `EI_EXPOSE_REP/REP2` 仅匹配 10 个具名嵌套 record——浅不可变载体。
新增任何非 Spring 类或非白名单 record 的同类问题都会重新被告警。

### D8 审计补差的即时缺口

- 生成类型漂移:openapi.json 已有漂移门禁,但 `api-types.gen.ts` 没有——`pnpm contracts:gen` 后
  `git diff --exit-code`,CI 与 `make check` 双执行。
- 迁移不可变:AGENTS「迁移只增不改」此前只有破坏性语句守卫;新增相对基线(PR base SHA / push
  before SHA)与工作树的 M/D/R 检查,Flyway 校验和失配提前到 CI。
- 本地/CI 对等:`make check` 补 `lint-config`(shellcheck/actionlint/yamllint/hadolint)与
  contracts 检查,消除「本地绿、CI 红」。
- 镜像 SBOM:container 作业在漏洞扫描外输出 CycloneDX,基础镜像 OS 包进入 SBOM。
- 平台侧:GitHub 原生 secret scanning + push protection 开启(公开仓库免费),与 gitleaks 形成
  「push 前拦截 + 历史扫描」双层。

### D9 数据库级 append-only 用 Java 迁移而非 SQL 厂商目录

Flyway 的 classpath 扫描是递归的:把 PG 专用 SQL 放进 `db/migration/postgresql/` 仍会被 H2
加载。`{vendor}` 占位符在本仓版本下未生效,故改用 Flyway Java migration:
运行时读取 `DatabaseMetaData.getDatabaseProductName()`,非 PostgreSQL 直接返回,PostgreSQL 创建
`BEFORE UPDATE OR DELETE` 触发器。该迁移仍受 Flyway 版本/校验和管理(不可变门禁同时覆盖
`src/main/java/db/migration`),PMD/Checkstyle 对迁移目录的命名/连接规则做目录级豁免并注明理由。

### D10 门禁不得静默降级

Testcontainers 的 `disabledWithoutDocker` 会让 PG 验证在无 Docker 时静默跳过。保留本地跳过
(开发机无容器时不阻塞),但在 CI 增加 surefire 报告断言:tests≥3、skipped/errors/failures 全 0,
否则作业失败。

## Risks
| 风险 | 缓解 |
|---|---|
| CodeQL 对 Java 25 字节码/构建的支持需 CI 首跑验证 | 手工构建 + JDK 25 与 backend 作业同版本;账单解除后首跑即知 |
| Trivy 首扫可能命中基础镜像 HIGH | `--ignore-unfixed` 过滤无解项;有解项按报错逐条升级 digest |
| Testcontainers 在 podman 上需 `DOCKER_HOST` + 关闭 Ryuk | 本地验证已用 podman socket 跑通;CI 用原生 Docker socket |
| 前端水位 36/37/27/39 仍偏低 | 明确为「只升不降」,后续功能批次补测上调 |
| SBOM 无哈希 | 记录为已知限制;需要哈希校验时再引入 cdxgen |
