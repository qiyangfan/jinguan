# 全栈升级至最新稳定版(Boot 4.1 / Java 25 / Node 24 / Vite 8)

## Why

**支撑窗口已破**:Spring Boot 3.4 的 OSS 支持终止于 3.4.13,3.5 也已于 2026-06-30 退出——当前 3.4.1 处于**无安全补丁线**,这是升级的硬理由而非尝鲜。Node 22 已进 maintenance LTS;前端 React 19.0/TS 5.7/Vite 6/react-router 7 各落后稳定主线 1–2 个大版。

**功能增量盘点(诚实结论)**:本轮没有出现「pi 1.0 原生支持 MCP」式的"框架原生能力替代手搓"红利——

- Spring Framework 7 的弹性注解只有 `@ConcurrencyLimit` 与 `@Retryable`(进 spring-core/context),**没有** `@RateLimit` 声明式限流(已核 7.0 Release Notes 与 spring.io 弹性博客);手搓的 `AuthRateLimitFilter`(Caffeine per-IP 固定窗口)**保留不动**,引入 Bucket4j 三方件换不来语义增益。
- 真实的增量收益在**构建与运行时**:Vite 8 以 Rolldown(Rust)为默认打包器(社区实测构建大幅提速)、TS 6 桥接版为 TS 7(Go 原生编译器)铺路、Java 25 LTS 与 Node 24 LTS 拉长支持窗口至 2028+。

**被迫随动项**:springdoc 2.x 不兼容 Boot 4,必须升 3.1.1——其输出信封切换为 OpenAPI 3.1,根级 `openapi.json`(三根管子契约机器可读权威)需一次受控重导出并重置 CI 漂移门禁基线;字段语义零变更(三管契约不受影响)。

## What Changes

- **后端运行时**:Java 21→25 LTS;Spring Boot 3.4.1→4.1.1;Spring Modulith 1.3.1→2.1.1;jjwt 0.12.6→0.13.0(无破坏性变更,仍 Jackson 2);Jackson 暂留 2.x(Boot 4 仍支持,7 处使用零改动)。
- **契约链**:springdoc 2.8.17→3.1.1(webmvc-api 变体),openapi.json 重导出为 OpenAPI 3.1 信封,前端 `api-types.gen.ts` 重生成;springdoc-openapi-maven-plugin 1.4→1.5。
- **门禁插件对齐 Java 25 字节码**:spotless 2.46.1→3.10.4、jacoco 0.8.13→0.8.15、spotbugs 4.9.6.0→4.10.5.0(pmd 3.28.0 不动)。
- **前端工具链**:Node 22→24 LTS;pnpm 11.24→12.14;Vite 6→8.3.4(Rolldown 默认);TypeScript 5.7→6.0.3(桥接版);react-router 7.1→8.4.0;React 19.0→19.3.1(RR8 peer 要求 ≥19.2.7);Tailwind 4.0→4.3.3、ESLint 10.10→10.13、TanStack Query 5.64→5.104、vitest 5.0→5.0.5、radix-ui 1.6→1.8.1、lucide-react 1.31→1.59、openapi-typescript 7.5→7.13 等小版本跟进;lockfile 重生成。
- **API 客户端收编(owner 定向,2026-10-18)**:`openapi-fetch` 0.17.0 替换手搓 `lib/api.ts` fetch 封装——路径/响应全类型化(`createClient<paths>`);既有语义(内存 access 注入、401 单飞刷新重放、RFC 7807 detail 中文转译、putEtag、AbortError 透传)以 middleware/包装层等价承载,14 组既有测试背书行为不变。
- **交付链**:Dockerfile 三阶段镜像换 `node:24-alpine` / `maven:3.9-eclipse-temurin-25` / `eclipse-temurin:25-jre`;CI `setup-java` 25、`setup-node` 24;compose PG 16→17。
- **文档**:README/AGENTS/openspec config.yaml 技术栈描述、contracts/control-plane.md 登记 OpenAPI 3.1 信封变化、HANDOFF 回写、演进登记 follow-up。

## Capabilities

### Modified Capabilities

- `delivery-pipeline`:新增「工具链支撑窗口」要求(构建/运行关键工具链必须处于上游 OSS 支持窗口内,升级走 change 流程)。

## Non-goals

- **不升 PostgreSQL 18**:Boot 4.0.x 管理 Flyway 对 PG 18 支持刚收口(spring-boot#49012);升 17 为安全步,18 连同 Flyway 管理版本核验登记 follow-up。
- **不升 Java 27 LTS**(2026-09 刚发布,生态认证滞后)与 **不直升 TypeScript 7**(typescript-eslint 支持刚落地、7.1 才有完整编程 API)——各登记 follow-up,本轮到 25 / 6.0.3。
- **不启用** `spring.threads.virtual.enabled`(行为变更,JPA 悲观锁资金路径收益不明确)。
- **不引入** Bucket4j/第三方限流,也**不改动** AuthRateLimitFilter / JwtAuthFilter / SpaWebConfig / WalletService 等既有手搓件的任何行为——本 change 是纯版本升级,业务与安全语义零变化。
- **不动**三根管子契约字段、`web/src/components/ui` 标准件(与 kongming 字节一致物——工具链升级不触碰组件源码,不触发跨仓同步流程)。

## Impact

- 改动面:`pom.xml`、`Dockerfile`、`.github/workflows/ci.yml`、`docker-compose.yml`、`web/package.json` + `web/pnpm-lock.yaml`(重生成)、`openapi.json` + `web/src/lib/api-types.gen.ts`(重导出)、技术栈描述文档。
- 回归面:`mvn verify` 全绿(Modulith verify / JaCoCo 60%/50% / PMD / SpotBugs / Spotless)+ 前端五门禁(typecheck/lint/lint:css/format:check/test)+ `pnpm build` + compose 全链路冒烟 + 认证后走查(亮暗两态)。
- 跨仓:muniu 零代码变更(契约字段零漂移,仅 openapi.json 信封 3.0→3.1 需在契约表登记告知);kongming 不受影响。
- 风险最高的两处:Boot 3.4→4.1 大版迁移(starter 改名/Security 7 DSL/Jackson 偏好/属性迁移)与 Vite 6→8(Rolldown 替换 Rollup),均有回落预案(见 design.md)。
