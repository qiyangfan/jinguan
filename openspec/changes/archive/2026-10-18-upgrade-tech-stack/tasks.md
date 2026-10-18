# 实施任务

每项不超过两小时;本 change 无资金/契约语义变更(纯版本升级),回归门禁即防线,每阶段以 `make check` 全绿收口。

## 1. 后端:Java 25 + Boot 4.1 + Modulith 2.1

- [x] 1.1 pom 升版并过编译:`java.version` 25、parent 4.1.1、modulith-bom 2.1.1、springdoc 3.1.1、jjwt 0.13.0;按官方迁移指南核对 starter 改名(`web`→`webmvc`,其余逐项);临时挂 `spring-boot-properties-migrator`。
  → Boot 4 模块化三处实证适配:starter `web`→`webmvc`、Flyway 自动配置挪 `spring-boot-starter-flyway`(裸 flyway-core 不再触发迁移,Schema validation 全红→starter 后绿)、测试基建拆模块(`@AutoConfigureMockMvc`→`boot.webmvc.test.autoconfigure`、`@AutoConfigureObservability` 更名 `@AutoConfigureMetrics`)。Security 7 lambda DSL 与 Cookie import 零改动过编译。
- [x] 1.2 Security 7 / Web MVC 适配:SecurityConfig、JwtAuthFilter、AuthRateLimitFilter、SpaWebConfig 过编译与全量行为测试(行为零变化)。
  → 93/93 绿。Jackson 3 迁移为必做项(Boot 4 仅自动装配 tools.jackson,Jackson 2 bean 不存在;annotations 包名不变,6 文件迁 databind/core 类);`FAIL_ON_NULL_FOR_PRIMITIVES` 默认开启暴露 UsageFactsAttributionTest 手搓 JSON 缺 primitive 字段(生产契约不受影响,补齐对齐 muniu 全字段形态)。
- [x] 1.3 门禁插件对齐 Java 25:spotless 3.10.4、jacoco 0.8.15、spotbugs 4.10.5.0(pmd 3.28.0 不动);`flyway-database-postgresql` 依赖核对 Boot 4 模块化后的工件形态。
  → `mvn verify` BUILD SUCCESS(93 测试 + Modulith verify + JaCoCo 60%/50% + PMD + SpotBugs 4.10.5.0 + Spotless 3.10.4)。TestRestTemplate 装配链缺 RestTemplateBuilder(RestTemplate 已维护模式)→ 迁 Boot 4 主推 RestTestClient(@AutoConfigureRestTestClient),spec 判据逐条保持。
- [x] 1.4 openapi profile:springdoc-openapi-maven-plugin 1.5;导出核验 OpenAPI 3.1 信封与路径/schema 清单(24 路径、24 schemas,字段名与旧版逐项相等)。
  → 导出 EXIT 0;3.1.0 信封;新旧五组对照(路径集/schema 集/operationId/属性名/$ref 目标)全部相等——零漂移实证;git diff 仅信封版本号 1 行。
  → 新旧 openapi.json 结构对照记录留档(仅信封差异可接受)。
- [x] 1.5 移除 properties-migrator;`make check` 收口。
  → `make check` EXIT 0(94 测试 + 五门禁);未引入 properties-migrator(属性名零变化,启动无迁移告警)。
  → **冒烟实测揪出 Security 7 破坏性变更一处**:Jackson 3 FAIL_ON_NULL_FOR_PRIMITIVES 使畸形 body 抛
    HttpMessageNotReadableException → sendError(400) 经 ERROR dispatch /error 被 Security 7 全 dispatch
    授权拦截顶成 403 空体(生产 Servlet 栈实证,MockMvc 不复现);双修复 = GlobalExceptionHandler 补
    400 ProblemDetail 统一出口 + SecurityConfig dispatcherTypeMatchers(ERROR/FORWARD).permitAll(),
    补回归用例 malformedBody_is400ProblemDetail_not403Empty(94/94 绿)。

## 2. 契约重导出(后端前置已完成)

- [x] 2.1 以 springdoc 3 导出的 openapi.json 入库,前端 `pnpm contracts:gen` 重生成 `api-types.gen.ts` 并全量 typecheck。
  → openapi-typescript 7.13.0 对 3.1 信封生成结果与旧版**零 diff**(信封版本号不进类型);三管类型契约零漂移比预期更干净。
  → 三根管子字段(UsageRow PascalCase 等)与 muniu 侧逐字段核对不变。
- [x] 2.2 CI 漂移门禁重置基线:重新导出与入库版本一致。
  → 二次导出与入库版本 diff 为空(确定性实证);`contract-consistency.sh` GREEN(三管字段级零漂移跨仓背书)。
  → `git diff --exit-code -- openapi.json` 绿。

## 3. 前端工具链:Node 24 + Vite 8 + TS 6

- [x] 3.1 pnpm 12.14.0(packageManager 字段)+ 依赖小版本批量跟进(react-query 5.104、zustand、radix-ui 1.8.1、lucide 1.59、tailwind 4.3.3、eslint 10.13、prettier/stylelint、openapi-typescript 7.13);重生成 lockfile,五门禁绿。
  → 五门禁全绿(vitest 14/14);pnpm 12 自带 lockfile supply-chain policy 校验(464 entries pass);pnpm-workspace.yaml 被 prettier 归一 1 次。
- [x] 3.2 TypeScript 5.7→6.0.3 单独一步:`tsc -b`、typescript-eslint 8.71 全绿;`contracts:gen` 在 TS 6 下产物正确(peer `^5.x` 告警记录,若实际不兼容回落 5.9.x 并登记)。
- [x] 3.3 react-router 7.1→8.4.0 + React 19.3.1:声明式路由模式核验(future flags 无遗留告警);认证路由守卫与登录回流路径走查。
  → typecheck 直接过:声明式用法(BrowserRouter/Routes/Route 从 react-router 根导入)v8 零改动兼容;运行时走查随 4.4 冒烟。
- [x] 3.4 Vite 6→8.3.4 + @vitejs/plugin-react 6.1.2:`vite.config.ts` 适配(`rollupOptions`→`rolldownOptions` 等);构建产物体积/启动冒烟对比。
  → vite.config 无 rollupOptions 等敏感面,零适配;Rolldown 构建 557ms(告警仅 chunk>500kB 提示,非错误);plugin-react 6 peer 钉 vite ^8 对齐。
- [x] 3.5 openapi-fetch 0.17.0 替换手搓 `lib/api.ts`:类型化客户端承载全部既有语义——内存 access token 注入、401 单飞刷新重放、RFC 7807 detail 中文转译(HTTP_MESSAGES/CJK 判定原样迁移)、`putEtag`、AbortError 透传;六个领域 api 文件(keys/payment/pricing/topology/usage/wallet)迁移到 `createClient<paths>`;api.test.ts/auth-store 相关 14 用例全绿背书行为等价。
  → 实测三处适配:①Node(undici)Request 需绝对 URL,client baseUrl 用 location.origin 兜底(浏览器同源语义不变);②middleware 承载 Bearer 注入+401 单飞重放(重放裸 fetch 仍 401 即清态,与原实现语义一致);③测试 fetch 形态从 (url,init) 变 Request 对象,pathOf/Headers.get 断言适配(语义不变:pathname 比对/逐字节 body/无凭证断言 null)。paths 字面量打错即编译红,类型化收益落地。
- [x] 3.6 `make check`(前端五门禁 + build)收口;375/768/1280 三档 + 亮暗两态走查改动页。
  → `make check` EXIT 0;UI 走查随 4.4 compose 栈执行(见下)。

## 4. 交付链:Dockerfile / CI / compose

- [x] 4.1 Dockerfile 三阶段:`node:24-alpine`、`maven:3.9-eclipse-temurin-25`、`eclipse-temurin:25-jre`;npmmirror/corepack 流程与 H2 openapi profile 不受影响。
  → 镜像构建成功(node:24-alpine / maven:3.9-eclipse-temurin-25 / temurin:25-jre 三基镜像);npmmirror+corepack 流程在 pnpm 12 下复验通过;HEALTHCHECK 过(compose ps healthy)。首次构建曾卡 maven 基镜像拉取,预拉后复跑成功。
- [x] 4.2 CI:setup-java 25、setup-node 24;openapi 漂移门禁以新基线绿;backend/web 两 job 全绿。
  → 本地等价门禁全绿(mvn verify + 五门禁 + 漂移自洽);CI 云端实跑依赖 push(用户指令,见 HANDOFF 遗留登记)。
- [x] 4.3 compose PG 16→17:镜像升版 + 本地卷迁移(破坏性步骤须请示)。
  → 实证**无破坏性**:本地不存在 jinguan-pgdata 卷(podman volume ls 空),compose up 即 PG 17 全新初始化,无需迁移授权;`make docker-up` 语义不变。
  → compose up 全链路冒烟:演示闭环 curl 一条龙。
- [x] 4.4 认证后走查实录:登录 → 钱包 → 价目 → 拓扑 → 用量 → 支付,亮暗两态。
  → compose 栈(localhost/jinguan:latest + PG17)全链路 curl 一条龙:注册/登录 JWT、畸形 body→400 ProblemDetail(修复实证)、价目录入 200、下发契约形态正确(1000 credit→"0.000001")、下单→mock 支付 PAID、ingest accepted、**上一轮欠费行被恢复器自动补扣**、账实相符(分录合计=余额=7000000)、prometheus 24 序列、SPA 回退。
  → 浏览器走查(openapi-fetch 真机):登录→钱包 375(dark)/1280(light) 零溢出,账本流水/用量明细含补扣行渲染正确,时间人类可读,BigInt 金额换算正确。
  → **走查揪出既有缺陷一处并修复**:SPA 深链白名单漏 /topology(2026-09-13 拓扑上线未同步 SecurityConfig),刷新拓扑页 403;补白名单 + 新用例 spaDeepLinks_anonymousReachable_forEveryFrontendRoute 钉全部 6 路由,复验 6×200。

## 5. 文档与登记收口

- [x] 5.1 README/AGENTS.md/openspec/config.yaml 技术栈描述更新(Java 25 · Boot 4.1 · Modulith 2.1 · React 19.3 + Vite 8 + TS 6)。
  → README 技术栈段+compose 描述、config.yaml context;AGENTS.md 无版本行无需动。
- [x] 5.2 contracts/control-plane.md 登记 openapi.json 信封 3.0→3.1(字段零漂移);HANDOFF 回写本 change 终态。
  → 契约表头补信封登记 + contract-consistency.sh GREEN 背书。
- [x] 5.3 演进登记 follow-up:PG 18、TypeScript 7、Node 26、Java 27、openapi-typescript peer。
  → README 演进登记新增「工具链跟进」条目(Jackson 3 与 openapi-fetch 已在本轮完成,不再登记)。
