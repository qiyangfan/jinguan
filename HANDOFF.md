# HANDOFF — 开工指引(唯一入口)

> 维护纪律:只写「当前状态 / 下一步 / 开放决策项 / 验收记录」四块,不写变更明细(归 git log),单次更新 ≤30 行。

## 当前状态(2026-09-13,五 change 归档 + 拓扑下发(第四根管子)落地)

- **五 change 已实施(close-revocation-loop / harden-money-ops / delivery-pipeline / harden-auth-session / record-usage-statements)**:`make check` 全绿(后端 33 测试含 JaCoCo/PMD/SpotBugs/Spotless 门禁 + 前端四门禁);迁移 V3-V5 落地;根级 `openapi.json` 导出(16 路径,两次生成 diff 为空=确定性实证)并接通前端 `contracts:gen`(顺带修复 PricingPage 误用 time_windows 的契约漂移);多阶段镜像构建成功 + compose 冒烟全链路通过(终态余额账实相符);前端时间显示统一人类可读(`formatDateTime`,YYYY-MM-DD HH:mm)。清单终态写回各 change tasks.md:**46 完成 / 0 阻塞**。
- **两项遗留阻塞已由用户解锁后收口(2026-09-12)**:①引入 vitest(前端五门禁就位):api.test.ts 3 组(401 单飞仅一次刷新/刷新失败清态广播/清态后无凭证外泄)+ auth-store.test.ts 4 组(bootstrap 恢复会话/失败回落/登出清态/登录解码)7/7 绿,接入 Makefile check 与 CI web job;②吊销 muniu 联调(独立实例 3s 收敛窗口):mk2 key 基线 502(鉴权过/上游拒连)→锦官吊销→一个拉取周期后 401 auth_failed,跨仓管子端到端打通。
- **深度复查已完成(2026-09-12,验收记录 2)**:此前审计的全部结论经逐文件亲证**零项被推翻**;新确认 2 个真实缺陷并入既有 change——①同 refId 并发幂等重放撞唯一约束转裸 500(insertOrReplay 先查后插,WalletConcurrencyTest 未覆盖同键并发)→ harden-money-ops 任务 3.2 + wallet-ledger spec MODIFIED;②登录用户名枚举(404/400 双差异)→ harden-auth-session 任务 3.3 + auth spec 增场景。无新增 change。
- 与 muniu 的三根管子:**全部接通**——价目下发、计量 ingest(契约测试钉死)、key 吊销拉取(本轮补齐,欠费停服+终态吊销)。
- **五 change 已全部归档(2026-09-13)**:`archive/2026-09-13-*` 五个,主 specs 合并 delta(+5/−3,活动 change 清零);收尾补全 delta 工件欠账——close-revocation-loop 补 REMOVED 两段(「吊销下发未实现(现状)」被「吊销下发端点」取代、「muniu 侧消费契约(对齐基准)」逐字段并入其正文,防合并后新旧矛盾并存),主 spec「key 绑定」补 MUST 关键词(RFC 2119 校验拦截点)。
- **拓扑下发=第四根管子已落地(2026-09-13,openspec `publish-topology` 已归档,新 capability topology-distribution)**:`GET /control/v1/topology`(ETag=payload 内容哈希双引号、If-None-Match 304、空态 200 空三件套)+ `PUT /admin/topology` 整包替换(V7 三表 topology_providers/routes/models;校验:routes 非空/引用完整/provider weight>0,key 只存 env 引用名绝不明文,相同内容重放 ETag 不变);SecurityConfig 放行同款网络隔离注记;契约测试 10 组逐字段对齐 muniu TopologyPayload;openapi.json 重导出(确定性 diff 空)+ 前端类型同步;**muniu 联调实录**(独立实例,poll=2s):首拉热替换 reused=0/created=1/removed=1 → 变更轮 reused=1/created=0/removed=0(池运行态无损)→ 锦官停机 WARN 保持当前快照、数据面存活;`make check` 全绿。

- **【用户定向】金额基座切换 μ→credit(1e-9)+ 出站 long 统一 string(2026-09-13,验收记录 4)**:muniu 计量契约实为 `CostCredits`(credit=1e-9 本币,muniu 选型注释明确否决 μ 基座),锦官原 `CostMicro`(1e-6)存在字段名+单位双重错位(真实链路将 100% 静默丢行/1000× 错扣)。已全链切换:V6 迁移(rename+存量×1000)、实体/端点/前端字段名、`creditsToDecimal` scale 9(同 muniu ParsePrice)、出站 long(ID+金额)统一 string(`@JsonSerialize(ToStringSerializer)`,JS 2^53 精度安全)、前端 money.ts BigInt 化、价目录入口径「元/百万 ↔ credit/token」×1000 换算(money.ts helper 单源)。

## 下一步(严格按序;涉及 openspec change 的:实施 → `make check` 全绿 → 审查迭代循环 → 归档)

1. 【用户】git push(e46a974 含五 change 实现;其后的五 change 归档 + publish-topology 拓扑下发已提交入库。铁律:逐次明确指令)。push 后确认 CI 三线实跑绿(delivery-pipeline 3.1 的遗留确认点)。
2. 【跨会话】backlog(触发条件到即立项,现在不动):兑换码(商业验证期自助收费,建议排在真实 PSP 之前)→ 真实 PSP adapter(含回调验签真实现 + queryStatus 对账)→ Prometheus 指标 → 邮件自助找回(等门户 email)→ 集群化(行锁/分布式锁/分布式限流)→ 持久化事件总线。README「演进登记」随归档同步收口。

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
3. **openspec 五 change 批量实施(2026-09-12,单遍批处理)**:判据 = 每 change 任务逐项实现 + `make check` 全绿 + 清单实时写回;资金/契约改动测试先行。证据 = ①吊销闭环:V3 迁移 + 全量快照端点契约测试(空态/单调/并发水位)+ 端到端(欠费→名单→充值→恢复→吊销→重绑 409,RevocationLoopIntegrationTest 独立 H2);②资金加固:归属 404/本人订单簿/V4 operator_user_id/admin debit(422 零痕迹)/恒时 token 比较/原子 markPaidIfCreated(顺带修复 markPaid 读-改-写竞态)/并发同键重放收敛(concurrent_sameRefId_credits_replay_notFailure);③交付管道:springdoc webmvc-api(webmvc-ui 与 SpaWebConfig /** 回退有 PathPattern 冲突,实证后弃用)/openapi.json 根级导出(16 路径,确定性 diff 空)/CI 漂移门禁/多阶段 Dockerfile(首次构建 outDir 修复一次)+ compose 冒烟全链路(余额 8,000,000 账实相符;宿主 rootless 端口转发瞬态丢响应已留证,容器内同请求 100% 成功);④认证会话:双 token(jti 防同秒重复)/cookie 四属性/轮换/AuthSession 集成测试/Caffeine 限流(独立上下文钉 429 边界)/统一登录话术/admin 重置密码;⑤用量明细:V5 usage_records/投影语义(accepted 计费终态不降级)/毒丸行明细可见断言/本人+管理查询/WalletPage 明细视图。遗留 3 阻塞项(需外部输入):吊销 muniu 联调、前端行为实证(无 vitest 基建)、CI 实跑(push 须用户指令)。`make check` 终验全绿(33 测试)。
4. **主 specs 金额基座回写 μ→credit + 账本规格矛盾清理(2026-09-13,openspec `credit-spec-rewrite`,6 任务全勾)**:判据 = 三主 specs(pricing-distribution/wallet-ledger/ingest-metering)与 V6 后实现口径一致 + grep 清零 + 归档合并成功。证据 = ①delta 归档合并(MODIFIED 4 + RENAMED 1:「管理员手工入账(现状)」经 RENAMED 语法更名「管理员手工入账」,正文对齐「资金操作审计」——修复 harden-money-ops 归档合并时只 ADDED 未 MODIFIED 留下的同文件自相矛盾);②delta 覆盖外残留人工同步 3 处:wallet-ledger Purpose 段 `SUM(amount_credits)`、超扣拒绝场景金额 credit 口径、pricing-distribution「改价即时生效」补缺失 scenario(既有 RFC 2119 欠账,归档校验阻塞物,与 close-revocation-loop 补 MUST 先例同性质);③文档层(README/docs/AGENTS)grep 证明零残留(验收记录 4 全链切换时已同步);④跨仓互引:muniu 仓同名 change 同日镜像执行(其 ADR-0006/主 specs/设计文档 6 处回写),跨仓契约「双方 change 同步」纪律首次双向留痕。归档位:`archive/2026-09-13-credit-spec-rewrite`。
5. **控制面契约单源钉死(2026-09-13,openspec `control-plane-contract-pinning`,5 任务全勾)**:判据 = 四管子契约有单源登记表 + 一致性脚本 GREEN 且报红自证 + 门禁绿。证据 = ①`contracts/control-plane.md` 契约登记表:四管子(pricing/key-revocations/ingest/topology)逐根登记端点/方向/形态权威方(/control 系=本仓 openapi.json,ingest 行=muniu UsageRow 序列化)/字段清单/金额单位 credit/双端锚点;topology 按已归档 publish-topology 事实登记为「已实现」(立项时的"未实现"前提已被并行会话超越);②聚合层 `scripts/contract-consistency.sh`:三组字段名级比对(UsageRow 20 字段↔IngestUsageRow、PricingView 9 字段↔muniu pricing.go、吊销键名双端、TopologyPayload schema)实跑 GREEN;报红自证 = 临时改名 CostCredits→RED→还原→GREEN;③门禁:`mvn verify` exit 0(零代码变更确认无涟漪)。新 capability `control-plane-contracts` 入主 specs;脚本不入仓(聚合共享层,前端同步脚本先例)。
