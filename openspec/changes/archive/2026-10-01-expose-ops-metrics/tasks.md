# expose-ops-metrics 实施任务

## 1. 测试先行(资金/契约类先红后绿,AGENTS.md 硬性纪律 7)

- [x] 1.1 失败测试(暴露面,**须跑真容器**):匿名 `GET /actuator/prometheus` → 200 + `text/plain` 且含 `jinguan_` 业务序列与 `application="jinguan"` 标签;负例 `/actuator/env`、`/actuator/configprops`、`/actuator/beans` → 403/404 且响应体不含端点数据;`/actuator/health` 仍由 actuator 承载
      **实施注记**:初版把本组断言写在 `@AutoConfigureMockMvc` 里,MockMvc 不含 actuator 端点映射,未注册路径落到 SPA 资源回退(200 + index.html),断言假绿/假红各半;已拆为两条落点——`OpsMetricsExposureTest` 取 `PathMappedEndpoints` 验白名单收口,`OpsMetricsReachabilityTest` 取 `RANDOM_PORT` + `TestRestTemplate` 验真容器可达(`text/plain;version=0.0.4`、`application="jinguan"`、零值序列在场),并把「暴露面判据必须真容器」写进 spec 判据
      **验证**:加 micrometer-registry-prometheus 依赖 + `management` 白名单段后转绿
- [x] 1.2 失败测试(ingest 分桶):一批含「正常/重放/未绑定/零成本/余额不足」各一行的投递 → `jinguan.ingest.rows` 五个 outcome 各 1;断言经 `MeterRegistry` 读数,不看 HTTP 响应体
      **验证**:`IngestService.accept` 末端计数后转绿
- [x] 1.3 失败测试(账本计数):入账生效、同 refId 重放、扣账至余额不足、同键不同载荷冲突 → `jinguan.wallet.operations` 四个 outcome 各自 +1,且 `kind` 维度正确
      **验证**:`WalletService` 出口与异常路径计数后转绿
- [x] 1.4 失败测试(订单计数与不双计):创建订单 → `orders{event=created}`+1;回调置付首次生效 → `{event=paid}`+1;重复回调不重复计数;过期收敛只动既有 `jinguan.payment.expiry.converged`(`orders` 无 `expired` 标签值);`jinguan.payment.expiry.backlog` Gauge 已注册且读数等于 `scheduler.backlog()`
      **验证**:`PaymentService` 计数 + 扫描器注册 Gauge 后转绿
- [x] 1.5 失败测试(管子活性):pricing / key-revocations 各一次整包 → `full`+1;topology 首次 `full`、带 `If-None-Match` 命中 → `not_modified`+1;管理页读 topology 不计入
      **验证**:三个 `*IngressController` 计数后转绿
- [x] 1.6 失败测试(低基数钉子):对两个不同用户各入账一次 → `kind="credit",outcome="applied"` 序列计数为 2,且注册表内该指标的序列组合数不随用户增长(断言无 uid 标签)
      **验证**:标签集合封闭(常量源)即绿

## 2. 实现

- [x] 2.1 `pom.xml`:新增 `io.micrometer:micrometer-registry-prometheus`(版本由 Boot BOM 管理,不写死)
- [x] 2.2 `common/MetricNames`:`final` 类 + 私有构造,登记全部指标名(含既有 `jinguan.payment.expiry.*` 三枚)、标签键(`outcome/kind/event/pipe`)与各封闭取值;Javadoc 中文注明「指标词汇表单源,新增必须同步 `docs/design/ops-metrics.md` 与测试;同一事实不得双计」
- [x] 2.3 `application.yml`:`management.endpoints.web.exposure.include: health,prometheus`、`management.metrics.tags.application: ${spring.application.name}`(注释写明白名单意图与 health 语义不可变原因)
- [x] 2.4 `security/SecurityConfig`:`/actuator/prometheus` 匿名可读规则 + 注记(与 `/control/v1/*` 同信任前提;README 演进登记指向);顺序必须先于 `anyRequest().authenticated()`
- [x] 2.5 计数落点:`IngestService`(六桶)、`WalletService`(`apply`/`debit` 出口 + `InsufficientFunds`/`IdempotencyConflict` 路径)、`PaymentService`(`createOrder`/`handleCallback`)、三个 `*IngressController`(含 304 分支)——构造注入 `MeterRegistry`,不引 AOP
- [x] 2.6 `PaymentExpiryScheduler`:既有 converged/scan 名称改用 `MetricNames` 常量引用(值不变),`backlog` 以 `Gauge.builder(...).supplier(this, "backlog")` 注册(消除「注释声称可观测但 getter 无人消费」)

## 3. 文档

- [x] 3.1 新增 `docs/design/ops-metrics.md`:指标表(名称/类型/标签/计数点/语义)+ 建议告警判据(`accepted` 归零、`rejected_conflict>0`、`control.pull` 增速归零)+ 与明细端点的分工(指标管趋势,明细管逐笔)+ 测试口径注记(`@AutoConfigureObservability` 与 SPA 回退遮蔽)
- [x] 3.2 `README.md`:新增「可观测」小节(抓取口径 + 文档链接);「演进登记」把「数据面拉取加认证头」与「采集面加认证头」合并为一条,契约表尾注同指该条(消除两套口径)
- [x] 3.2+ 随行:`topology/ProviderPresetCatalog` 的 Gemini 备注由「原生 gemini 桥接 muniu 未实现」更正为「muniu 不建原生桥接,值域单源不含 gemini」(与 muniu 侧 `retire-gemini-adapter-token` 裁决对齐;该文案无测试钉值)
- [x] 3.3 `HANDOFF.md`:当前状态 + 验收记录(判据→证据→日期),≤30 行纪律

## 4. 收尾

- [x] 4.1 `mvn -q verify` 全绿(含 JaCoCo 60%/50%、PMD、SpotBugs、Spotless、Modulith verify)
- [x] 4.2 `cd web && pnpm typecheck && pnpm lint && pnpm lint:css && pnpm format:check && pnpm test` 全绿(本批前端零改动,须实证无涟漪)
- [x] 4.3 契约零漂移实证:`make openapi` 重导出 `contracts/openapi.json` 与根级 `openapi.json`,git diff 为空(springdoc 不扫 actuator)
- [x] 4.4 冒烟实录:`curl /actuator/prometheus` 取 `# TYPE jinguan_` 行与 `grep -c` 结果贴 HANDOFF;确认 `/actuator/env` 404
- [x] 4.5 `openspec validate --specs --strict` 全绿(新 capability `ops-metrics` 的 Purpose 为真实文本非占位)
