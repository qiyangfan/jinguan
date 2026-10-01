# 运维指标面(单源:代码 `common/MetricNames` + 本文)

锦官是持钱包账本的控制面。指标只回答两类问题:**趋势是否异常**、**链路是否还活着**。
逐笔读数一律走明细端点(`GET /wallet/history`、`GET /wallet/usage`、`GET /admin/usage`、`GET /payments/orders`),
不用时间序列代替账本。

## 抓取口径

- 端点:`GET /actuator/prometheus`(`text/plain; version=0.0.4`),与业务 API 同端口(默认 8090);
- 暴露面白名单:`management.endpoints.web.exposure.include: health,prometheus`。
  `env`/`configprops`/`beans`/`mappings`/`heapdump`/`loggers`/`threaddump` **不暴露**——它们能读出配置项名与
  环境变量引用名;该约束由 `OpsMetricsExposureTest` 钉死;
- 信任前提:与 `/control/v1/*` 三根管子同一条(匿名可读 + 部署网络隔离)。发布形态 `docker-compose.yml`
  绑定 `127.0.0.1:8090`。**跨网段采集前必须先落地 README 演进登记的「拉取/采集统一加认证头」**。
- 所有序列带 `application="jinguan"` 标签。
- 测试注意:Boot 3.4 的 `@SpringBootTest` 默认关闭指标导出,断言暴露面必须加 `@AutoConfigureObservability`。
  判据分两条落点:①**白名单是否收口**取 `PathMappedEndpoints`(Boot 自己登记的暴露清单)——MockMvc 下
  actuator 路径会被 SPA `/**` 回退遮蔽(未命中端点映射也返回 200 + index.html),拿测试内 HTTP 状态码断言
  「env 不暴露」会得出与生产相反的结论;②**端点是否真可达且真是 0.0.4 文本**取 `RANDOM_PORT` +
  `TestRestTemplate`(`OpsMetricsReachabilityTest`)——真容器里 prometheus 未装配就会落回 index.html,
  Content-Type 断言当场变红。两条都是 spec 的硬 MUST,缺一不可。

## 指标表

| 指标(Prometheus 名) | 类型 | 标签(封闭枚举) | 计数点 | 语义 |
|---|---|---|---|---|
| `jinguan_ingest_rows_total` | Counter | `outcome` ∈ accepted / replayed / skipped_unbound / skipped_free / skipped_insufficient | `IngestService` 逐行分类即计 | 计量行归宿分桶,与 `IngestResult` 同源;`received` 为各桶之和,不单独计。**不得改为批末统一推进**:`accept` 不开类级事务、每行各自提交,批内后续行抛幂等冲突(409)时已提交行会永久丢计 |
| `jinguan_wallet_operations_total` | Counter | `kind` ∈ credit / debit;`outcome` ∈ applied / replayed / rejected_insufficient / rejected_conflict | `WalletService.credit/debit`(唯一资金通道) | applied/replayed **提交后**才计(外层事务回滚不虚增);两类拒绝为终态事实,立即计 |
| `jinguan_payment_orders_total` | Counter | `event` ∈ created / paid | `PaymentService.createOrder` / `handleCallback` 首次转换 | 重推/并发败方回调走 replayed 分支,不重复计 paid;过期事实**不在这里** |
| `jinguan_payment_expiry_converged_total` | Counter | — | `PaymentExpiryScheduler` | 累计 `CREATED → EXPIRED` 收敛笔数(唯一承载过期事实的序列) |
| `jinguan_payment_expiry_scan_seconds_*` | Timer | — | `PaymentExpiryScheduler` | 单轮扫描耗时 |
| `jinguan_payment_expiry_backlog` | Gauge | — | `PaymentExpiryScheduler::backlog` | 最近一轮已过期未收敛存量(积压) |
| `jinguan_control_pull_total` | Counter | `pipe` ∈ pricing / key_revocations / topology;`outcome` ∈ full / not_modified | 三个 `*IngressController` **成功成包之后** | 数据面是否还在拉、ETag 命中率;管理页复用 service **不计入**。计数一律后置到成包后:读库故障也计 `full` 会让「下发链路断」判据在最该触发时永不成立 |

JVM / HTTP / HikariCP 等默认序列由 micrometer 白送(`http_server_requests_seconds_*` 的 URI 是模板化路径,
不含真实 ID,不会基数爆炸)。

## 基数纪律

禁止把用户 ID、订单号、refId、厂商名等无界取值用作标签。账本指标的合法标签集只有
`application/kind/outcome`,由 `OpsMetricsIntegrationTest` 断言(多用户入账不新增时间序列)。

## 建议告警判据

判据成立的前提:**零值序列必须在启动期就存在**。Prometheus 的 `increase(...)==0` 对【不存在】的序列返回
空向量,比较根本不成立——也就是说「重启后 100% skipped、accepted 一路为零」这种最该告警的形态会静默。
下表全部判据因此由 `CommittedMetrics#registerClosedSeries` 按封闭词表预建零值序列支撑
(新增 outcome/kind/event/pipe 取值时必须同步该表,否则新桶在第一次出现前不可告警)。

| 现象 | 判据 | 为什么 |
|---|---|---|
| 静默丢行 | `increase(jinguan_ingest_rows_total{outcome="accepted"}[30m]) == 0` 而 muniu 侧仍有流量 | 金额基座/字段名错位事故的形态就是「100% 不生效但 HTTP 200」(见 HANDOFF 验收记录 4) |
| 归账失败积压 | `increase(...{outcome="skipped_unbound"}[15m]) > 0` 持续增长 | key 未绑定 = 用量无法归账,需运营补绑定 |
| 幂等异常 | `increase(jinguan_wallet_operations_total{outcome="rejected_conflict"}[15m]) > 0` | 同 refId 不同载荷 = 上游重投语义被破坏,属事故级 |
| 下发链路断 | `increase(jinguan_control_pull_total[10m]) == 0` | 数据面停止拉取:价目/吊销名单/拓扑全部失效,比业务错误更静默 |
| 停服风暴 | `increase(jinguan_ingest_rows_total{outcome="skipped_insufficient"}[15m])` 陡增 | 大面积欠费或价目配置错误 |
