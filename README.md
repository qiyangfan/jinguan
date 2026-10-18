# jinguan · 锦官

> 「决敌之资,唯仰锦耳」——蜀锦是蜀汉经济命脉,锦官署即国家经济中枢。
> muniu 平台控制面:用户体系 · 钱包账本 · 支付抽象 · 渠道与价目下发(PG 权威)。

ADR-0005 裁决的独立控制面产品(另立仓库):muniu 是数据面,锦官是它背后的
用户、钱包与计价中枢。与数据面仅以 HTTP+JSON 契约耦合(协议耦合,非代码耦合)。

## 架构

Spring Boot + **Spring Modulith**(模块依赖 CI 门禁 + 模块间领域事件)+
六边形(真边界端口)+ DDD 分级建模(核心域深、支撑域简),按业务能力切模块:

| 模块 | 职责 | 要点 |
|---|---|---|
| `user` | 注册/登录(JWT,首用户 ADMIN) | 支撑域,建模从简、纪律一致 |
| `wallet` | ★ 钱包账本(核心域) | append-only、refId 幂等、余额非负不变量、per-user 条带锁内含完整事务 |
| `payment` | 支付单状态机 + `PaymentProvider` 出站端口 | mock 渠道演示闭环;订单超时双保险(**懒过期守门保资金正确性 + 游标批量扫描收敛状态**,方案谱系见 [docs/design/payment-timeout.md](docs/design/payment-timeout.md));真实 PSP = 未来一个 adapter |
| `pricing` | 渠道/价目运营 + 下发契约端点 | 对齐 muniu `admin.PricingView` 逐字段 |
| `topology` | 拓扑三件套运营 + 下发契约端点(第四根管子) | ETag=内容哈希;官方厂商预设目录(`GET /admin/topology/presets`)供管理页一键回填 |
| `ingest` | 计量接收 → 账本扣账 | 对齐 muniu `UsageRow` PascalCase 负载,Bearer 静态 token |
| `security` | JWT + Spring Security(横切) | 无状态;`@AuthenticationPrincipal` 注入 uid |

纪律:全库禁 public setter(工厂+意图方法);词汇统一(`*Service`=应用服务、
`*Repository`=持久化端口);Modulith `verify()` 进 CI——跨模块依赖违规即红。

## 与 muniu 的契约(四根管子)

| 契约 | 方向 | 形态 |
|---|---|---|
| 价目下发 | 锦官 → muniu 拉取 | `GET /control/v1/pricing` → `{"pricing":[PricingView]}`(input/multiplier 去尾零字符串) |
| key 吊销 | 锦官 → muniu 拉取 | `GET /control/v1/key-revocations` → `{"version","revoked"}`(版本单调) |
| 计量 ingest | muniu → 锦官推送 | `POST /ingest/usage` `{"rows":[UsageRow]}`(⚠ Go 无 json tag,PascalCase)+ Bearer |
| 拓扑下发 | 锦官 → muniu 拉取 | `GET /control/v1/topology` → `TopologyPayload`(providers/model_routes/models;ETag 条件请求 304) |

⚠ muniu 的两个拉取目前不带认证头(数据面现实),`/control/v1/*` 依赖部署网络
隔离保护;为数据面增加拉取认证头是双方演进项(与采集面 `/actuator/prometheus` 合为一条,见下文「演进登记」)。ingest 已带 Bearer。

key 吊销下发已实现(2026-09-12):**终态吊销**(`key_revocations` append-only,
ADMIN `POST /admin/keys/{id}/revoke`,不可逆)+ **欠费停服**(`users.suspended_at`,
ingest 余额不足自动置位、任何入账同事务自动清除——充值即恢复)合并为全量快照,
版本经 `sync_watermarks` 与状态变更同事务单调递增;重绑已吊销 key 返回 409。

待结算恢复已实现(2026-10-02,openspec `recover-pending-usage-settlement`):
计量行的接收与结算责任分离——`POST /ingest/usage` 的 2xx 表示该批用量已**可靠落库**
(每行先校验不可变计费事实并预留明细,同 ID 不同事实报 409),不表示全部已扣款。
欠费行留存为已固定归属的待结算事实(`pending`/`skipped_insufficient` 投影),
充值(或管理员入账)提交后由 `UsageSettlementScheduler` 单实例周期恢复自动补扣
(默认 30s 一轮,每轮 ≤100 用户 × 100 行,用户间按最近结算尝试时间公平轮转;
重启、扣账后投影中断、预留后中断均可恢复,资金经 `usage:{ID}` 幂等键最多生效一次),
不依赖数据面重投。首次合法解析的归属固定,key 重绑不转嫁既有用量。

## 演示闭环(一条龙)

```bash
# 注册(首用户自动 ADMIN) → 手工入账 → mock 支付 → 入账 → 计量扣账
curl -s :8090/auth/register -d '{"username":"a","password":"password-123"}' | jq .token
curl -s :8090/wallet/admin/credit -H "Authorization: Bearer $T" \
  -d '{"userId":1,"amountCredits":10000000000,"refId":"seed-1"}'
curl -s :8090/payments/orders -H "Authorization: Bearer $T" \
  -d '{"provider":"mock","amountCredits":2000000000,"currency":"CNY"}'
curl -s :8090/payments/mock/po-xxx/pay          # 模拟收银台支付 → 账本入账
curl -s :8090/wallet/balance -H "Authorization: Bearer $T"   # 余额已变
```

## 运行与测试

```bash
# PG(JINGUAN_DB_URL 默认指向本机 5433;可用任意 PG 实例)
mvn spring-boot:run          # Flyway 自动建表
mvn test                     # 全绿含:演示闭环集成测试 + Modulith 结构门禁
```

容器化(本机 podman 形态):`make docker-build` 构建自包含镜像(web 产物入 jar,
仓库不提交静态构建残留);`make docker-up` 起 PG 17 + 应用(数据落命名卷
jinguan-pgdata,`make docker-down` 保留卷);冒烟:`curl :8090/actuator/health`。

技术栈:Java 25(LTS)· Spring Boot 4.1 · Spring Modulith 2.1(结构门禁进 CI)· JPA ·
Flyway · PostgreSQL(测试 H2 兼容模式,列类型避开 PG 专有;生产 PG 权威);前端
React 19 + Vite 8(Rolldown)+ TypeScript 6 + Tailwind 4 + openapi-fetch(类型化客户端,
契约单源 openapi.json)。

## 可观测

`GET /actuator/prometheus`(与业务同端口;暴露面白名单只含 `health` 与 `prometheus`,`env`/`configprops`
一类可读配置的端点不注册)。指标词汇表、计数点与告警判据见
[docs/design/ops-metrics.md](docs/design/ops-metrics.md):账本写入、计量行归宿、支付状态机、三根拉取型管子的活性。
指标只管趋势与告警,逐笔读数走账本历史与用量明细端点。

## 演进登记

- Event Publication Registry(事件持久化+重放):当前支付入账为同事务强一致;
  规模化后切换持久化事件实现最终一致。
- 收款维持 Mock(2026-10-02 owner 终态裁决,无限期):真实 PSP 不排期、不评审;`PaymentProvider` 出站端口契约保留,重启条件 = owner 明确立项(出现真实收款需求),此前任何会话不得将其列为待办或开放项。本轮待结算恢复与 Key ID 绑定亦按此边界不接真实渠道。
- 采集面认证头与 muniu 拉取认证头(**同一演进项,2026-10-02 终态登记**)、key 代签(自校验 key 联动 ADR-0006):`/control/v1/*` 与
  `/actuator/prometheus` 当前同信任前提——匿名可读 + 部署网络隔离(compose 绑定 `127.0.0.1:8090`)。
  **裁决:当前单主机回环形态下不构成现实风险,本条不排期、不反复评审**;硬触发条件 = 多主机/跨网段部署
  (拉取或采集跨信任域)立项时,双方(本仓 + muniu cluster 侧消费方)联合 change 一并落地,含 muniu 侧拉取认证头
  配置与既有 cluster 部署样例迁移。
- 集群化:per-user JVM 条带锁 → 账本行锁/分布式锁。
- 工具链跟进(2026-10-18 upgrade-tech-stack 后存量):PG 18(待 Boot 管理 Flyway 对 18 的支持矩阵核验)、
  TypeScript 7(待 7.1 稳定编程 API + typescript-eslint 完整适配,当前 6.0.3 桥接)、Node 26(2027 观察)、
  Java 27(下一 LTS 周期)、openapi-typescript 对 TS 6 的 peer 声明(产物已实证无碍)。
- 调度器线程池:两个 `@Scheduled` 任务(订单过期收敛 + 待结算恢复)共享 Spring Boot 默认单线程调度器,
  串行执行。积压场景下一轮恢复可长时间占住调度线程,拖慢过期扫描的及时性(资金正确性不受影响——
  过期有回调懒守门兜底)。两者出现互相拖延时,设 `spring.task.scheduling.pool.size: 2` 即解除。
- 前端:深浅主题已就位(token 单源+防 FOUC+切换持久化);响应式(窄屏顶部导航/表格横滚/表单换行)已就位;国际化(i18n 文案抽取)随门户多语言需求排期——控制面当前单语中文,与 kongming 前端现状一致。
