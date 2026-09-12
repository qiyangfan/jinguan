# HANDOFF — 开工指引(唯一入口)

> 维护纪律:只写「当前状态 / 下一步 / 开放决策项 / 验收记录」四块,不写变更明细(归 git log),单次更新 ≤30 行。

## 当前状态(2026-09-12,差距审计完成 + openspec 立项)

- **MVP 已就位**:六模块(user/wallet/payment/pricing/ingest/security)+ 演示闭环 + Modulith 门禁 + JaCoCo/PMD/SpotBugs 三道 CI 静态门禁;工程纪律(append-only 账本、refId 幂等、per-user 锁内含事务、并发测试)好于同类开源(one-api/new-api/Lago)。
- **差距审计已完成(2026-09-12,验收记录 1)**:对照 one-api/new-api/Lago/OpenMeter 逐项盘点,关键发现——①**资金闭环断一环**:`GET /control/v1/key-revocations` 契约登记但零实现(无端点/无表/无管理操作),muniu 拉取循环已就绪在等(auth.go:221),现行为 = 欠费用户无限免费使用;②订单查询无归属校验(水平越权);③admin 资金操作不记操作人;④无容器化交付;⑤前端 `contracts:gen` 引用的 openapi.json 不存在,类型链路断;⑥单 access token 12h 不可吊销;⑦/ auth/* 无限流;⑧ingest 明细不留存(用户无法对账)。
- **openspec 已立项**(本仓 `openspec/`):6 份基线 specs(auth-session/wallet-ledger/payment-orders/pricing-distribution/ingest-metering/key-lifecycle,如实含现状缺口)+ 5 个活动 change(见下一步);对齐 muniu/kongming 家规(config.yaml 含项目上下文与硬性纪律指针)。
- **深度复查已完成(2026-09-12,验收记录 2)**:此前审计的全部结论经逐文件亲证**零项被推翻**;新确认 2 个真实缺陷并入既有 change——①同 refId 并发幂等重放撞唯一约束转裸 500(insertOrReplay 先查后插,WalletConcurrencyTest 未覆盖同键并发)→ harden-money-ops 任务 3.2 + wallet-ledger spec MODIFIED;②登录用户名枚举(404/400 双差异)→ harden-auth-session 任务 3.3 + auth spec 增场景。无新增 change。
- 与 muniu 的三根管子:价目下发、计量 ingest 已实现且契约测试钉死;**key 吊销拉取是当前唯一缺水管子**,补齐即闭环。

## 下一步(严格按序;每个 change:先红后绿 → `make check` 全绿 → 审查迭代循环 → 归档)

1. 【执行会话】`close-revocation-loop`:终态吊销(append-only key_revocations)+ 欠费停服(users.suspended_at,充值即恢复)+ `GET /control/v1/key-revocations` 全量快照(sync_watermarks 版本源,逐字段对齐 muniu)。设计决策见 change design.md(D1-D6)。
2. 【执行会话】`harden-money-ops`:订单归属校验(404 不泄露)+ 本人订单列表 + ledger 加 operator_user_id + admin debit 调账 + ingest token 恒时比较。
3. 【执行会话】`delivery-pipeline`:springdoc 导出根级 openapi.json(修复前端 contracts:gen 断链)+ CI 漂移门禁 + 多阶段 Dockerfile + compose。**建议排在 auth 之前**:契约链路修好,后续 change 的 API 形状变更自动带文档。
4. 【执行会话】`harden-auth-session`:双 token(access 30min 内存态 + refresh 7d httpOnly cookie 轮换,单飞刷新)+ /auth/* per-IP 限流(Caffeine)+ admin 重置密码。设计决策见 change design.md。
5. 【执行会话】`record-usage-statements`:usage_records 明细先行落库 + 本人/管理查询 + 钱包页明细视图。
6. 【跨会话】backlog(触发条件到即立项,现在不动):兑换码(商业验证期的自助收费闭环,建议排在真实 PSP 之前)→ 真实 PSP adapter(含回调验签真实现 + queryStatus 对账任务)→ Prometheus 指标(ingest 拒绝数/扣账失败数/回调数)→ 邮件自助找回(等门户 email)→ 集群化(行锁/分布式锁/分布式限流)→ 持久化事件总线。README「演进登记」随各 change 归档同步收口。

## 开放决策项

本批决策由架构师代裁(依据见各 change design.md);用户可推翻,推翻须同步改对应 spec。

| 日期 | 现象 | 备选 | 裁决 |
|---|---|---|---|
| 2026-09-12 | 欠费如何停服 | 终态吊销 key(充值须重绑 key)/ 余额阈值推导 / 双语义拆分 | **双语义拆分**:终态吊销(security)与欠费停服(business,充值即恢复)分存两处、合并下发——混用会把可逆问题做成不可逆;与 muniu 402→限流冷却的生命周期判断同源 |
| 2026-09-12 | 吊销下发形态 | 增量协议 / 全量快照 | **全量快照 + sync_watermarks 单调版本**:muniu 消费语义即全量替换(增量会被当全量洗掉未吊销 key);通用 watermark 表供后续管子复用 |
| 2026-09-12 | 会话形态 | 单 token 12h / 全 cookie + CSRF / 双 token | **双 token**:access 30min 仅内存 + refresh 7d httpOnly cookie 轮换;不做复用检测与黑名单(单实例规模过度设计,触发条件已登记) |
| 2026-09-12 | 操作人审计 | 独立审计表 / 账本加列 | **ledger 加 operator_user_id 可空列**:账本兼作审计是既定纪律,不为非资金操作先建审计表 |
| 2026-09-12 | 用量明细一致性 | 双写强一致 / 明细先行幂等落库 | **明细先行**(usage_id 唯一,先落明细再扣账):final_outcome 是投影可更新,不属资金事实,账本 append-only 纪律不约束 |
| 2026-09-12 | mockPay 是否加认证 | 加认证 / 匿名保留 | **匿名保留**:模拟 PSP 托管收银台,orderId 服务端生成不可枚举 = capability URL,与真实渠道形态一致 |

## 验收记录(阶段闸门时追加:判据 → 证据 → 日期)

1. **差距审计与 openspec 立项(2026-09-12)**:判据 = 对照同类开源逐项盘点、每项结论有 file:line 证据、决策有被否决备选、规格可归档。证据 = 全量源码盘点(40 Java 文件 + web + CI + 迁移)+ muniu 消费端核验(auth.go:221-288:全量替换/version>=单调/60s/失败保持快照/无认证头)+ 6 基线 specs + 5 change(proposal/design/tasks/delta 齐全);实施判据 = 各 change tasks.md 逐项 + `make check` 全绿。
2. **深度复查(2026-09-12,亲证循环)**:判据 = 既有审计每项结论经本人直读代码核验(不采信代理转述)、新缺陷须有代码证据方可入规格。证据 = SecurityConfig/JwtService/JwtAuthFilter/PaymentController/IngestController/IngestService/UserService/WalletService/WalletController/ApiKeyBindingController/PaymentService/MockOrderEntity/PaymentExpiryScheduler/LedgerEntryRepository/GlobalExceptionHandler/SpaWebConfig/PricingService/api.ts/auth-store.ts 全文亲读;grep 亲证 key-revocations 零实现、openapi.json 双路径不存在、JaCoCo 0.60/0.50 与 CI 门禁。结论 = 既有 5 change 结论零推翻;新增确认 2 缺陷(并发同键重放 500 化/登录枚举)并入既有 change(harden-money-ops 3.2+harden-auth-session 3.3),未新开 change;非缺陷澄清 2 处(SpaWebConfig 仅 SPA 回退无 CORS/muniu 消费语义维持)。
