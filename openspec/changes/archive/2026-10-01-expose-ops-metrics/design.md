# expose-ops-metrics 设计

## D1:用官方 micrometer registry,不移植 muniu 的零依赖实现

muniu 手搓 Prometheus 文本协议(`internal/metrics`,ADR-0009)成立的前提是它的硬约束:单二进制、依赖白名单制、禁止引框架。锦官是 Spring Boot 应用,`micrometer-registry-prometheus` 就是同一个问题的工业标准答案,且 JVM/HTTP/连接池等基础指标由它白送。把 muniu 的实现搬过来 = 在允许用轮子的地方重复造轮子,还要自己维护直方图分桶与文本转义。

版本由 `spring-boot-starter-parent` BOM 管理,`pom.xml` 不写 `<version>`(与本仓既有依赖口径一致)。

## D2:指标与业务同端口,不开独立 management 端口

`management.server.port` 不设,`/actuator/*` 继续留在 8090。

- 独立端口的真实代价:Spring Boot 会把**全部** actuator 端点搬到 management 子上下文,`/actuator/health` 随之离开 8090 —— `Dockerfile:36` 的 HEALTHCHECK(`curl http://localhost:8090/actuator/health`)与 compose 端口映射都得改,一处部署链改动换来的只是「指标端点不过 security 链」;
- 而 security 链本来只多一条规则,且同端口能保住既有 health 语义不被顺手破坏(交付链 3.1 的冒烟判据依赖它);
- 暴露面控制交给 D3 的白名单,而不是靠端口隔离。

## D3:暴露面 = 白名单 include + 负例测试

```
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
```

显式只放两个:`env`/`configprops`/`beans`/`mappings`/`heapdump` 这些能读出配置项名、bean 拓扑乃至密钥引用名的端点一律不可达,并由测试断言 404/403 —— 这类端点一旦误开,网络隔离前提就不成立了。用 include 白名单而非 exclude 清单:新增端点默认不暴露,漂移方向是安全的。

`management.metrics.tags.application: jinguan` 给所有序列打应用标签,单集群多服务时抓取侧可区分。

## D4:计数点落在领域 choke point

| 指标 | 计数点 | 为什么在这儿 |
|---|---|---|
| `jinguan.ingest.rows` | `IngestService.accept` 返回前,按六桶逐一累加 | 桶值与 `IngestResult` 同源;放 Controller 会把「HTTP 200」误当「行被处置」 |
| `jinguan.wallet.operations` | `WalletService` 的 `apply` / `debit` 出口与异常路径 | AGENTS.md 账本纪律:一切资金写入必经此处。计数放这里,「绕过账本服务」在指标上直接显形 |
| `jinguan.payment.orders` | `PaymentService.createOrder` / `handleCallback`(首次生效才计 paid) | 三态中 `expired` **不在此计数**——`PaymentExpiryScheduler` 已用 `jinguan.payment.expiry.converged` 承载同一事实(`:40`),重复计数会产生两套必然分叉的读数;`markPaidIfCreated` 返回值区分首次生效与重复回调 |
| `jinguan.control.pull` | 三个 `*IngressController` 出口(含 304 分支) | 「有没有人在拉」只有端点层知道;service 被管理页复用调用时不应污染拉取计数 |
| `jinguan.payment.expiry.backlog`(Gauge) | `PaymentExpiryScheduler` 构造期注册,取值函数即既有 `backlog()` | 类注释已声称「可观测暴露用」,但 `backlog()` 全仓零消费方且无导出通道——注册成 Gauge 是兑现既有承诺,不是新增设计 |

既有 `jinguan.payment.expiry.converged` / `.scan` 保持名称与埋点位置不变,只把名称登记进 `common/MetricNames` 单源。原则:**一个事实一个序列**,新增计数器前先查是否已有同语义指标。

**否决:AOP 环绕 `@Transactional` 服务方法计数**。「方法被调用」不等于「资金事实发生」——幂等重放、余额不足拒绝、参与外部事务时回滚,都是需要单独桶的事实,只有领域内部知道。

## D5:指标名与标签常量单源放 `common`

`common/MetricNames`(`final` 类 + 私有构造 + `public static final String`)集中登记 4 个指标名、标签键与各标签值。理由:

- 阿里手册/PMD「禁魔法值」在本仓是门禁;
- `control.pull` 被三个不同模块的 Controller 使用,分散常量即三处手抄——正是 muniu `adapter.Types()` 刚收拾过的同类问题;
- `docs/design/ops-metrics.md` 的指标表与此一一对应,文档抄错可被测试断言反查。

Modulith 侧:各模块依赖 `common` 是既有事实(`InsufficientFundsException` 等已跨模块使用),不新增依赖方向。

## D6:低基数是硬约束,不是建议

指标面 MUST NOT 出现 uid / orderId / refId / provider 名 等无界维度标签;时间序列总数上界 = 4 个指标 × 各自枚举笛卡尔积(约 20 条序列) + JVM/HTTP 默认序列。按用户的读数需求由明细端点承担(`GET /wallet/history`、`UsageRecordController`、`/admin/usage`)。HTTP 默认指标 `http.server.requests` 保留 Boot 默认的模板化 URI(不带查询串与真实路径变量),不会因路径参数产生基数爆炸。

## D7:认证口径与 `/control/v1/*` 合并为同一条演进项

现网 `docker-compose.yml:33` 是 `127.0.0.1:8090:8090`,不发布公网;`/control/v1/*` 三根管子本就匿名可读(数据面 GET 不带认证头的现实契约)。指标端点采取同一前提,并在 README 演进登记里把「数据面拉取 + 采集面」合并成一条「加统一认证头(需 muniu 侧同步 change)」的演进项——避免同一个部署前提欠两笔账、两套说法。

被否决:给 `/actuator/prometheus` 单独造静态 token 过滤器。ingest 的静态 Bearer 之所以成立,是因为它是**写路径且跨仓契约已定义**;采集是只读、由部署方 Prometheus 配置决定,先按网络隔离上线,等拉取认证头一并解决。

## 后果

- `accepted=0` 这类静默丢行、`rejected_conflict>0` 这类幂等异常,变成可告警的时间序列;
- 四根管子中三根的活性(有无拉取方、ETag 命中率)第一次有数可读;
- 指标词汇表有单一代码源 + 一份设计文档,新增计数器必须同时改两处并有测试兜底。
