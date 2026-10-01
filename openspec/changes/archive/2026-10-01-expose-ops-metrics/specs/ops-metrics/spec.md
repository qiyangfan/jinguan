## Purpose

控制面的运维可读性:指标端点的暴露面(只放 `health` 与 `prometheus`)、资金与计量链路的计数器词汇表(名称/标签/计数点单源)、以及采集端的认证前提与告警口径。指标负责趋势与告警,逐笔读数仍由账本与用量明细端点承担。

## ADDED Requirements

### Requirement: 指标暴露面为白名单

系统必须 (MUST) 经 `/actuator/prometheus` 暴露 Prometheus 文本格式指标(`text/plain;version=0.0.4`),并必须 (MUST) 以白名单方式配置 Web 暴露端点集合,当前只含 `health` 与 `prometheus`;能读出配置项名、环境变量引用名或 bean 拓扑的端点(`env`/`configprops`/`beans`/`mappings`/`heapdump` 等)必须 (MUST NOT) 以 actuator 语义可达——响应状态必须为 403 或 404,且响应体必须 (MUST NOT) 含端点数据快照。所有时间序列必须 (MUST) 带 `application` 标签(值取 `spring.application.name`)。指标端点必须 (MUST) 与业务 API 同端口提供,且 `/actuator/health` 的既有路径与 actuator 承载语义不得改变(镜像 HEALTHCHECK 依赖之)。

暴露面判据必须 (MUST) 在真实 Servlet 容器(RANDOM_PORT)下回归:MockMvc 环境不含 actuator 端点映射,未注册的 actuator 路径会落到 SPA 资源回退并返回 200 + `index.html`,验证不出「端点是否注册」与「白名单是否收口」。

#### Scenario: 指标端点可读
- **WHEN** 匿名 `GET /actuator/prometheus`(真容器)
- **THEN** 200,`text/plain` 版本 0.0.4 格式,含 `jinguan_` 前缀的业务序列与 JVM/HTTP 默认序列,每条带 `application="jinguan"`

#### Scenario: 配置类端点不暴露
- **WHEN** `GET /actuator/env`、`/actuator/configprops` 或 `/actuator/beans`
- **THEN** 403 或 404,响应体不含 `activeProfiles`/`propertySources` 等端点数据特征

### Requirement: 业务计数器词汇表单源且低基数

系统必须 (MUST) 在单一常量源登记业务指标名、标签键与标签值(枚举封闭),并必须 (MUST) 覆盖:`jinguan.ingest.rows{outcome}`、`jinguan.wallet.operations{kind,outcome}`、`jinguan.payment.orders{event}`、`jinguan.control.pull{pipe,outcome}`,以及既有 `jinguan.payment.expiry.converged`、`jinguan.payment.expiry.scan`、`jinguan.payment.expiry.backlog`(Gauge,取扫描器最近一轮的已过期未收敛存量)。同一资金事实必须 (MUST) 只由一个序列承载(禁止为已过期订单再造 `orders{event=expired}` 之类的重复读数);既有指标的名称必须 (MUST) 保持不重命名。指标标签必须 (MUST NOT) 含用户 ID、订单号、refId、厂商名等无界取值(时间序列基数失控为反模式);按用户/订单的读数必须 (MUST) 由账本历史与用量明细端点提供。

#### Scenario: 一个事实一个序列
- **WHEN** 一批过期订单被扫描收敛
- **THEN** 只有 `jinguan_payment_expiry_converged_total` 增长,`jinguan_payment_orders_total` 不含 `expired` 标签值

#### Scenario: 无高基数标签
- **WHEN** 同一钱包对多个不同用户反复发生入账
- **THEN** `jinguan_wallet_operations_total` 仍只有 `kind`×`outcome` 有限组合的序列,不随用户数增长

#### Scenario: 指标清单可反查
- **WHEN** 新增一个业务计数器
- **THEN** 名称与标签值必须先在常量源登记,并在指标设计文档成表;测试按常量断言,文档与代码分叉即红

### Requirement: 资金链路计数落在账本服务内

账本写入次数必须 (MUST) 由 `WalletService` 自身计数(唯一资金通道),分桶必须 (MUST) 区分:入账/扣账(`kind=credit|debit`)× 生效/幂等重放/余额不足拒绝/同键载荷冲突(`outcome=applied|replayed|rejected_insufficient|rejected_conflict`)。禁止 (MUST NOT) 以 HTTP 层「请求成功」替代「资金事实发生」。计量接收的分桶必须 (MUST) 与 `IngestResult` 六桶语义同源(accepted/replayed/skipped_unbound/skipped_free/skipped_insufficient 各计一次)。

#### Scenario: 余额不足可见
- **WHEN** 一行用量因余额不足被跳过(账本零痕迹)
- **THEN** `jinguan_ingest_rows_total{outcome="skipped_insufficient"}` 与 `jinguan_wallet_operations_total{kind="debit",outcome="rejected_insufficient"}` 各加一

#### Scenario: 同键不同载荷冲突可见
- **WHEN** 同一 refId 以不同用户或金额重放,抛幂等冲突
- **THEN** `outcome="rejected_conflict"` 计数加一,且该笔不产生账本分录

### Requirement: 拉取型管子的活性可见

三根拉取型下发端点(`GET /control/v1/pricing`、`/control/v1/key-revocations`、`/control/v1/topology`)每次被调用必须 (MUST) 计入 `jinguan.control.pull{pipe,outcome}`,`outcome` 必须 (MUST) 区分整包下发(`full`)与条件请求命中(`not_modified`,仅 topology 具备 ETag 语义);计数点必须在端点层,管理页复用 service 的路径不得计入。

#### Scenario: 数据面停止拉取可被发现
- **WHEN** muniu 侧停止拉取超过一个告警周期
- **THEN** `jinguan_control_pull_total` 增速归零,与 `accepted` 仍增长(或同时归零)可组合成「下发链路断」的告警判据

### Requirement: 采集端点的信任前提与演进登记

`/actuator/prometheus` 必须 (MUST) 与 `/control/v1/*` 采用同一信任前提:匿名可读、依赖部署侧网络隔离(发布形态仅绑定 `127.0.0.1`,不发布公网)。该前提必须 (MUST) 在 README 演进登记中与「数据面拉取加认证头」合并为一条统一演进项登记,禁止 (MUST NOT) 分两处登记造成两套口径;部署到多主机场景前,该演进项必须 (MUST) 先落地。

#### Scenario: 现网绑定不外露
- **WHEN** 以仓库 `docker-compose.yml` 起服
- **THEN** 指标端口只在本机回环可达(`127.0.0.1:8090`),跨网段采集需另行部署且先补认证
