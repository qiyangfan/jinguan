# expose-ops-metrics 提案:控制面运维指标暴露与资金链路计数器

## Why

锦官是持钱包账本的控制面,但**指标面处于「埋了点、开了个不存在的口子」的状态**,逐条亲证:

- `pom.xml:62` 只有 `spring-boot-starter-actuator`(micrometer-core 靠它传递引入),**没有 `micrometer-registry-prometheus`**;
- `src/main/resources/application.yml` 全文无 `management:` 段,Boot 默认 Web 暴露仅 `health` —— 即 `/actuator/prometheus` 这个端点**当前根本不存在**,没有 registry 能把 meter 导出去;
- 唯一已埋点的类是 `payment/PaymentExpiryScheduler.java:40-45`:`jinguan.payment.expiry.converged` Counter + `jinguan.payment.expiry.scan` Timer,类注释(`:21`)写着「可观测:… backlog 已过期未收敛存量」,而 `backlog()`(`:49`)只是 getter,全仓除自身外**零消费方**,也没注册成 Gauge。注释承诺的暴露通道从未存在,这批 meter 现在是写入即蒸发;
- 其余资金 choke point 零埋点:`WalletService`(唯一资金通道)、`IngestService.accept`(六桶归宿)、三根拉取型管子(有没有人在拉)。

对照数据面:muniu 把指标当一等公民(`internal/metrics` 零依赖 Prometheus 文本协议 + `data-plane-observability` 主 spec 在册)。**同一个平台两侧,只有钱的那一侧是瞎的。**

这不是「锦上添花」,而是本仓已经付过一次学费的盲区:`HANDOFF.md` 验收记录 4 的 μ→credit 基座错位事故,性质是「真实链路 100% 静默丢行/1000× 错扣」——`IngestService.accept`(`IngestService.java:40`)对每行都算得出归宿,但结果只回给当次 HTTP 响应,批与批之间不累计,没人能在下轮投递时看出「连续 N 小时 accepted=0」。

## What Changes

1. **暴露面**:新增 `micrometer-registry-prometheus` 依赖;`application.yml` 增 `management` 段——Web 暴露**白名单式**只放 `health,prometheus`(显式排除 `env`/`configprops`/`beans`/`mappings` 等可读配置与密钥引用名的端点),`management.metrics.tags.application=jinguan`。
2. **补齐指标面并收编既有埋点**(命名与标签集合固定,低基数):
   - `jinguan.ingest.rows`(tag `outcome`:accepted / replayed / skipped_unbound / skipped_free / skipped_insufficient)——桶值与 `IngestResult` 同源,静默丢行由此可告警;
   - `jinguan.wallet.operations`(tag `kind`:credit / debit,`outcome`:applied / replayed / rejected_insufficient / rejected_conflict)——计数点落在 `WalletService` 这个唯一资金通道内,绕过即不可见(与 AGENTS.md 账本纪律同构);
   - `jinguan.payment.orders`(tag `event`:created / paid)——**过期收敛复用既有 `jinguan.payment.expiry.converged`,不重复计数**(同一事实两套序列必然分叉);
   - `jinguan.control.pull`(tag `pipe`:pricing / key_revocations / topology,`outcome`:full / not_modified)——四根管子中三根「拉取型」的活性与 ETag 命中;
   - `jinguan.payment.expiry.backlog` **Gauge**:把 `PaymentExpiryScheduler.backlog()` 注册为可采序列,兑现该类注释「可观测:… backlog 已过期未收敛存量」从未落地的承诺;
   - 既有 `jinguan.payment.expiry.converged` / `jinguan.payment.expiry.scan` 名称保持不动(已提交代码,重命名无收益),但一并登记进 `common/MetricNames` 单源与指标文档,消除「一半有源一半散落」。
3. **认证口径**:`/actuator/prometheus` 与 `/control/v1/*` 同前缀族治理——匿名可读 + 依赖部署网络隔离(`docker-compose.yml:33` 现网即 `127.0.0.1:8090:8090`,不发布公网);把「给数据面拉取与采集面统一加认证头」合并为一条演进项登记,不再分两处欠账。
4. **测试**(先红后绿,资金类):指标端点可达性与**未暴露端点负例**(断言 `/actuator/env`、`/actuator/configprops` 不可达);四个计数器各自的标签值经真实调用路径累加(ingest 分桶、wallet 入账/扣账/不足/冲突、payment 创建/回调、control 全量与 304)。
5. **文档**:README「演进登记」补采集面口径与指标清单;`docs/design/ops-metrics.md` 登记指标面(名称/标签/语义/告警建议)作为指标词汇表单源;`HANDOFF.md` 状态与验收记录。

## Non-goals

- **不自研指标协议**。muniu 手搓 Prometheus 文本格式成立的前提是「单二进制零依赖」硬约束(AGENTS.md 铁律 3 依赖白名单);Spring Boot 侧 `micrometer-registry-prometheus` 就是该问题的成熟答案,自研等于重复造轮子。
- **不引链路追踪 / OTel / 日志指标化**。当前诉求是「量能不能看出来」,不是「单次请求跨仓定位」;跨仓定位已有 ingest 明细表(`usage_records`)与审计列承担。
- **不做 per-user / per-order 维度标签**。uid 与 orderId 作标签会让时间序列基数无界(Prometheus 反模式),且账本明细查询(`GET /wallet/history`、`UsageRecordController`)已能按用户精确读数——指标负责趋势与告警,明细负责逐笔。
- **不重命名既有指标**:`jinguan.payment.expiry.converged` / `.scan` 是已提交代码里的名称,改名没有读数收益,反而让历史文档与本 change 失配;只做「登记进单源 + 补 Gauge + 补暴露通道」。
- **不改 `/actuator/health` 现有语义**(Dockerfile HEALTHCHECK 依赖它,见 `Dockerfile:36`)。
- **不动 openapi 契约面**:springdoc 未开启 actuator 扫描,`contracts/openapi.json` 与前端生成类型零漂移(收尾以导出 diff 为空实证)。

## 决策点(详见 design.md)

- D1 官方 micrometer registry,而非移植 muniu 的零依赖实现;
- D2 指标与业务同端口(`management.server.port` 不设),而非独立管理端口——独立端口会把 `/actuator/health` 从 8090 搬走,连带改 Dockerfile HEALTHCHECK 与 compose 端口映射,为省一条 security 规则不值得;
- D3 暴露面走「白名单 include」+ 负例测试,而非依赖默认值;
- D4 计数点落在领域 choke point(WalletService / IngestService / IngressController),不在 Controller 层用 AOP 环绕——后者会把「资金写入是否真发生」与「HTTP 是否成功」混为一谈;
- D5 认证沿用 `/control/v1/*` 的网络隔离前提,并合并为单一演进项。

## Impact

- **依赖**:新增 `io.micrometer:micrometer-registry-prometheus`(由 Boot BOM 管版本,不写死版本号)。
- **代码**:`application.yml`(management 段)、`SecurityConfig`(1 条匹配规则 + 注记)、`IngestService`、`WalletService`、`PaymentService`、`PaymentExpiryScheduler`、三个 `*IngressController`、新增 `common/MetricNames`(指标名与标签常量单源,满足阿里手册「禁魔法值」与本仓 PMD 门禁)。
- **schema**:零改动(不新增表/列,指标不落库)。
- **测试**:新增 1 个集成测试类;既有 33 项测试预期零回归(构造函数注入变化由 Spring 上下文装配,无手工 new)。
- **运维**:多一个可读端点 `GET /actuator/prometheus`;compose/镜像无需改端口拓扑。
