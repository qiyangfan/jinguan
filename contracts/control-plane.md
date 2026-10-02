# 锦官 ↔ 木牛 控制面契约登记表(单源)

> 本表是跨仓契约问答的**唯一事实源**(openspec capability `control-plane-contracts`)。
> 变更纪律:任何管子的字段/形态变更必须双方 change 同步,两仓 HANDOFF 互引留痕;
> 聚合层 `../scripts/contract-consistency.sh` 报红的字段级漂移须在任一侧归档前修复。
> 金额单位全局统一:**credit(1 credit = 本位币 1e-9,整数,零浮点)**。

## 管子一览

| # | 管子 | 端点 | 方向 | 形态权威方 | 状态 |
|---|---|---|---|---|---|
| 1 | 价目下发 | `GET /control/v1/pricing` | 锦官 → muniu | 锦官 `openapi.json` `PricingView`/`PricingEnvelope`(springdoc 导出 + CI 漂移门禁) | ✅ 已实现,双端联调 |
| 2 | key 吊销快照 | `GET /control/v1/key-revocations` | 锦官 → muniu | 锦官 `openapi.json`(匿名端点,响应内联构造) | ✅ 已实现,吊销 502→401 联调实证 |
| 3 | 用量上报 | `POST /ingest/usage` | muniu → 锦官 | **muniu** `UsageRow` JSON 序列化(产生方为准,Go 无 tag = PascalCase) | ✅ 已实现,契约测试钉死 |
| 4 | 拓扑下发 | `GET /control/v1/topology` | 锦官 → muniu | 锦官 `openapi.json` `TopologyPayload`/`ProviderDef`/`RouteCandidate`/`CapabilityDef`/`KeyRef` | ✅ 已实现(publish-topology,ETag 304 + 联调实录) |

## 管子 1:价目下发

- 响应形态:`{"pricing":[PricingView]}`(`PricingEnvelope`);`PricingView` 字段(snake_case,9):
  `alias, source, currency, input, cached_input, output, timezone, time_windows, updated_at`
- 金额:input/cached_input/output 为 **credit 十进制字符串**(scale 9 去尾零,0→"0";`PricingService.creditsToDecimal` ↔ muniu `ParsePrice` ≤9 位)。
- 双端锚点:锦官 `PricingIngressController.java` / `PricingService.java`;muniu `internal/admin/pricing.go` `ParsePricingRecord`(管理面与控制面拉取同一解析器)、`cmd/muniu/main.go` 拉取循环(60s,失败保持)。
- 认证:匿名 + 网络隔离(双方登记演进项:拉取认证头)。

## 管子 2:key 吊销快照

- 响应形态:`{"version":int64,"revoked":["key_id",...]}`,**全量快照**(非增量),version 单调(muniu 仅应用 version ≥ 本地),空态 `{"version":0,"revoked":[]}`。
- 名单语义:终态吊销(`key_revocations`)∪ 欠费停服用户名下全部绑定 key;排序确定性(TreeSet)。
- 双端锚点:锦官 `KeyRevocationsIngressController.java`(响应构造)/`KeyRevocationService.java`(insert+水位单事务);muniu `internal/auth/auth.go`(版本单调防回退、默认 60s、失败保持上一次快照并告警)。
- 收敛窗口 = muniu 拉取间隔(有界可配,联调实证 3s 配置下吊销 502→401)。

## 管子 3:用量上报(ingest 行形态 = 显式契约)

- 请求形态:`{"rows":[IngestUsageRow...]}`,Bearer `ingest_token`(恒时比较);行字段 **PascalCase**(muniu Go 结构体无 json tag 的默认序列化),与 muniu `internal/store/store.go` `UsageRow` 逐字段对齐(20):
  `ID, RequestID, TenantID, APIKeyID, Provider, ModelAlias, UpstreamModel, Stream, Status, ErrorClass, PromptTokens, CompletionTokens, ReasoningTokens, CachedTokens, Estimated, CostCredits, Currency, LatencyFirstTokenMs, LatencyTotalMs, CreatedAt`
- 语义锚点:`CostCredits` 整数 credit;`CreatedAt` UTC RFC3339;`Status ∈ {ok, error, client_cancel}`;行幂等键 `usage:{ID}`;断网整批落 muniu `metering_buffer` 按序续传。
- **接收与结算责任分离(recover-pending-usage-settlement,2026-10-02)**:2xx = 该批需要处理的用量已可靠落库(结算可以暂缓),不表示全部已扣款;存储失败或同 ID 不同不可变计费事实 → 非 2xx(muniu 续传重试)。欠费行留存为待结算事实(`skipped_insufficient`/`pending` 投影),充值后由锦官侧 `UsageSettlementScheduler`(单实例周期恢复,默认 30s)补扣——**不依赖 muniu 重投业务欠费行**;`IngestResult` 六计数形态不变,后台恢复不计接收指标。
- 双端锚点:muniu `internal/metering/sink.go` `IngestSink`(产生方,2xx 即结束运输重试);锦官 `IngestUsageRow.java`(逐字段镜像)/`IngestController.java`(Bearer 恒时比较)/`IngestService.java`(逐行先可靠预留再进唯一资金通道,refId 幂等)/`UsageSettlementScheduler.java`(待结算恢复)。
- **新增计量字段流程**:muniu UsageRow 加列 → 本表先行更新(双方 change)→ 锦官 record 同步 → 脚本双绿 → 归档。

## 管子 4:拓扑下发

- 响应形态:`TopologyPayload`(providers/routes/capabilities;`ProviderDef` 含 `key` = env 引用名,**绝不明文**;详见 `openapi.json` components 4 schema)。
- 条件请求:ETag = payload 内容哈希双引号,`If-None-Match` 304;空态 200 空三件套;损坏/不完整快照 muniu 侧拒绝保持旧快照。
- 双端锚点:锦官 `TopologyIngressController`(V7 三表 topology_providers/routes/models,`PUT /admin/topology` 整包替换);muniu `internal/config/topology_source.go` `FetchTopology` + `internal/proxy/topology.go` 原子热替换。

## 一致性校验

聚合层脚本(不入仓):`../../scripts/contract-consistency.sh` —— 三组字段名级比对:
1. muniu `UsageRow` 结构体字段 ↔ 锦官 `IngestUsageRow.java` record 字段;
2. `openapi.json` `PricingView` properties ↔ muniu `internal/admin/pricing.go` 源码;
3. 吊销快照键名(`version`/`revoked`)双端在位。
职责边界:只抓字段名级漂移,不验语义;报红人工复核。
