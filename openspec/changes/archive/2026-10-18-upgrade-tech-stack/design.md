# 设计:全栈升级决策记录

> 调研截至 2026-10-18。版本号均以仓库元数据(repo1.maven.org / npm registry)直查为准,不凭记忆。

## 1. 版本矩阵与裁决

### 后端

| 组件 | 现状 | 最新稳定 | 裁决 | 依据 |
|---|---|---|---|---|
| Java | 21(2023 LTS) | 25 LTS / 27 LTS(2026-09) | **25** | Framework 7 明示推荐 JDK 25;27 满月即升、生态认证滞后 |
| Spring Boot | 3.4.1 | 4.1.1(4.2 尚 M2) | **4.1.1** | 3.4/3.5 均已出 OSS 窗口;4.0 的 OSS 支持止于 2026-12-31,4.1 是唯一长窗线 |
| Spring Modulith | 1.3.1 | 2.1.1(2.2 尚 M2) | **2.1.1** | 随 Boot 4.1 对齐的稳定线 |
| springdoc | 2.8.17 | 3.1.1 | **3.1.1** | Boot 4 必须 v3 线;输出 OpenAPI 3.1 |
| jjwt | 0.12.6 | 0.13.0 | **0.13.0** | 唯一变更是构造函数可见性放宽,仍基于 Jackson 2 |
| Jackson | 2.x(Boot 管理) | 3.x(`tools.jackson`) | **暂留 2.x** | Boot 4 仍支持 Jackson 2;仓内 7 处 `com.fasterxml` 使用(含 JPA 属性转换)零改动;3.x 迁移登记独立 change |
| PostgreSQL | 16(compose) | 18.x | **17** | Boot 4.0.x 管理 Flyway 报 PG 18 "Unsupported Database"(spring-boot#49012);17 在支持矩阵内且迁移面小 |
| Maven | 3.9 | 3.9.x | **3.9**(镜像换 temurin-25 标签) | Maven 4 不在本轮范围 |
| 门禁插件 | spotless 2.46.1 / jacoco 0.8.13 / pmd 3.28.0 / spotbugs 4.9.6.0 | 3.10.4 / 0.8.15 / 3.28.0 / 4.10.5.0 | **除 pmd 外全部跟进** | Java 25 字节码解析需要;jacoco 0.8.13 对 25 仅试验性支持 |

### 前端

| 组件 | 现状 | 最新稳定 | 裁决 | 依据 |
|---|---|---|---|---|
| Node | 22(maintenance) | 24 LTS / 26(2026-10-28 才转 LTS) | **24** | 24 支持至 2028-04;26 十天后才转 LTS,不抢跑 |
| pnpm | 11.24.0 | 12.14.0 | **12.14.0** | 与 Node 24 搭配的当前主线;lockfile 版本随升 |
| Vite | 6.0.7 | 8.3.4 | **8.3.4** | Rolldown 默认、Node ≥20.19;`@tailwindcss/vite@4.3.3` peer 已含 `^8`、`@vitejs/plugin-react@6.1.2` peer 钉 `^8.0.0`(已核对) |
| TypeScript | 5.7.2 | 7.0.2 / 6.0.3(桥接) | **6.0.3** | `typescript-eslint@8.71.2` peer 明确 `<6.1.0`(支持 6.0);TS 7 的 typescript-eslint 支持刚落地、完整编程 API 要等 7.1 |
| react-router | 7.1.0 | 8.4.0 | **8.4.0** | 官方称破坏面极小(future flags 转正);peer 要求 React ≥19.2.7、Node ≥22.22、Vite ≥7,本轮全满足;声明式(非 framework)模式受影响面更小 |
| React | 19.0.0 | 19.3.1 | **19.3.1** | RR8 peer 前置;19.2+ 含 `<Activity>`、`useEffectEvent` 等稳定 API,可选使用 |
| 其余 | Tailwind 4.0 / ESLint 10.10 / react-query 5.64 / vitest 5.0 / radix-ui 1.6.7 / lucide 1.31 / openapi-typescript 7.5 | 4.3.3 / 10.13 / 5.104.4 / 5.0.5 / 1.8.1 / 1.59.0 / 7.13.0 | **全部小版本跟进** | 无破坏面;vitest 最新稳定即 5.0.5(同线) |

## 2. 手搓替代评估(结论:本轮无原生替代红利)

| 手搓件 | 候补原生能力 | 裁决 |
|---|---|---|
| `AuthRateLimitFilter`(Caffeine per-IP 固定窗口) | Framework 7 弹性注解**仅** `@ConcurrencyLimit` + `@Retryable`(spring.io 2025-09-09 博客、7.0 Release Notes 双确认),无 `@RateLimit` | **保留**。Bucket4j/bucket4j-spring-boot-starter 是三方件,违背 pom 里"零三方风险"选型注释,且分布式语义本就不需要(单实例,与 ShedLock 登记一致) |
| `JwtAuthFilter` + `JwtService`(jjwt) | Spring Security 7 无新增 JWT 装配 | 保留,jjwt 升 0.13.0 即可 |
| `SpaWebConfig`(SPA 回退) | Framework 7 无静态资源处理新能力 | 保留 |
| `lib/api.ts`(fetch + 401 单飞刷新 + 错误转译) | `openapi-fetch@0.17.0`(与 openapi-typescript 同作者的 typed client,middleware 可承载刷新) | **本轮实施**(owner 定向 2026-10-18,不拖独立 change):`createClient<paths>` 全类型化,middleware 承载 token 注入与 401 刷新重放,转译层原样迁移;14 组既有测试背书行为等价 |
| `UsageSettlementScheduler`(fixedDelay 恢复器) | `@Retryable`/`@ConcurrencyLimit` 语义不匹配(需要的是有界轮转扫描) | 保留 |
| API 版本协商(三根管子无此需求) | Framework 7 原生 API versioning | 不采用——契约字段冻结、URL 已带 `/v1` |

## 3. 被否决备选

- **Boot 3.4→3.5 中转再上 4**:3.5 同样已出 OSS 窗口,中转无支撑意义;直接到 4.1.1,属性迁移用 `spring-boot-properties-migrator` 兜底(迁移完移除)。
- **Java 27**:发布仅一个月,Hibernate/SpotBugs/JaCoCo 等字节码消费方认证普遍滞后一个 LTS 周期;25 是 Framework 7 的推荐线,工具链全绿。
- **TS 直升 7.0.2**:7 月刚稳定;typescript-eslint 官方支持 issue(#12518)刚收口且 7.0 无稳定编程 API(7.1 解决)。6.0.3 桥接版与 7 同语言语义,升 7 时应用代码零改动。
- **Node 26**:转 LTS 在 2026-10-28,距实施不足两周;新 LTS 首月镜像/CI 生态抖动概率高。
- **Vite 停在 7.3.7**:无必要——两个关键插件的 peer 范围均已含 Vite 8;但保留回落预案(见 §4)。
- **Jackson 2/3 的最终取舍以实证为准**:提案时预判"Boot 4 对 Jackson 2 仍提供自动配置,暂留 2.x";任务 1.x 实施时以 starter 依赖树与 ObjectMapper bean 装配实证——若 Boot 4.1 已移除 Jackson 2 自动配置,则迁移为必做项,在同一任务内完成(包名 `com.fasterxml`→`tools.jackson`,JPA 转换器/限流 Filter/序列化注解 7 处),不拆分不拖延(owner 铁律:能力可及不登记 follow-up)。
- **openapi-fetch 替换 api.ts 拆独立 change**(已被 owner 否决,2026-10-18):类型化收益与会话态/转译链路重写均在本轮回归门禁覆盖内,当轮做完。

## 4. 风险与回落预案

1. **Boot 3.4→4.1 迁移面**(最高风险):starter 改名(已知 `spring-boot-starter-web`→`spring-boot-starter-webmvc`,其余以官方迁移指南逐项核对)、Security 7 DSL、Flyway 模块化、JUnit 线升级。逐项过 [官方迁移指南],每小步 `mvn verify` 兜底;测试基建无 `@MockBean` 使用(已 grep 确认),测试面小。
2. **OpenAPI 3.1 信封切换**:springdoc 3 默认输出 3.1,`openapi.json` 会出现一次大 diff——这是**预期重置**,不是契约漂移。流程:导出后与旧版做路径/schema 清单对照(24 路径、24 schemas、字段名逐一相等),确认仅信封与 `$schema` 形态差异,再重置 CI 漂移门禁基线;前端 `contracts:gen` 重生成并全量 typecheck。muniu 侧如解析 openapi.json 需知会(HANDOFF 登记)。
3. **Vite 8 / Rolldown**:`vite.config.ts` 若用 `build.rollupOptions` 须改 `build.rolldownOptions`;CJS interop 行为差异可能暴露隐式导入。回落预案:`vite@7.3.7` + `@vitejs/plugin-react` 对应 5.x(两者成对降),Tailwind 插件 4.3.3 本身兼容 5–8。
4. **openapi-typescript peer `^5.x` vs TS 6.0.3**:peer 范围未含 6.0(pnpm 默认仅告警不阻断)。任务中显式核验 `contracts:gen` 产物正确性;若实际不兼容,回落 TS 5.9.x 并在演进登记。
5. **H2 MODE=PostgreSQL**:Boot 4.1 管理的 H2 小版本升级可能暴露兼容差异——全量测试即门禁;openapi profile 的 H2 启动路径同样复验。
6. **PG 16→17 卷迁移**:本地 `jinguan-pgdata` 卷不能直接换镜像启(数据目录大版不兼容),需 pg_dump/restore 或重建卷——**破坏性步骤,实施时单独请示授权**(运维铁律);生产 PG 不在本仓范围。
7. **pnpm 11→12**:lockfile 格式随大版变化,CI `--frozen-lockfile` 依赖本地重生成后提交;corepack 下载源流程(Dockerfile COREPACK_NPM_REGISTRY)不变,复验即可。

## 5. 实施顺序依据

先前端工具链(风险低、反馈快)还是先后端(风险高)?**先后端**:Boot/springdoc 是 openapi.json 重导出的前置,而 openapi.json 又是前端 `contracts:gen` 的输入——顺序必须是 后端 → 契约重导出 → 前端工具链(含类型重生成)→ 交付链(Dockerfile/CI/compose)→ 文档收口。前端工具链中 TS 6 与 Vite 8 分两步提交,失败可独立回退。
