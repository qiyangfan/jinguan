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
隔离保护;为数据面增加拉取认证头是双方演进项。ingest 已带 Bearer。

key 吊销下发已实现(2026-09-12):**终态吊销**(`key_revocations` append-only,
ADMIN `POST /admin/keys/{id}/revoke`,不可逆)+ **欠费停服**(`users.suspended_at`,
ingest 余额不足自动置位、任何入账同事务自动清除——充值即恢复)合并为全量快照,
版本经 `sync_watermarks` 与状态变更同事务单调递增;重绑已吊销 key 返回 409。

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
仓库不提交静态构建残留);`make docker-up` 起 PG 16 + 应用(数据落命名卷
jinguan-pgdata,`make docker-down` 保留卷);冒烟:`curl :8090/actuator/health`。

技术栈:Java 21 · Spring Boot 3.4 · Spring Modulith 1.3 · JPA · Flyway · PostgreSQL
(测试 H2 兼容模式,列类型避开 PG 专有;生产 PG 权威)。

## 演进登记

- Event Publication Registry(事件持久化+重放):当前支付入账为同事务强一致;
  规模化后切换持久化事件实现最终一致。
- 真实 PSP adapter:契约(`PaymentProvider`)已冻结,商业验证后实现一个渠道。
- muniu 拉取认证头、key 代签(自校验 key 联动 ADR-0006):双方对齐后排期。
- 集群化:per-user JVM 条带锁 → 账本行锁/分布式锁。
- 前端:深浅主题已就位(token 单源+防 FOUC+切换持久化);响应式(窄屏顶部导航/表格横滚/表单换行)已就位;国际化(i18n 文案抽取)随门户多语言需求排期——控制面当前单语中文,与 kongming 前端现状一致。
