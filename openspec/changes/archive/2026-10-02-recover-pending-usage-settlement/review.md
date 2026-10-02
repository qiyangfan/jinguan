# 提案核验

状态：提案阶段，未实施。

- 已核对 2xx 运输确认、余额检查前置、投影降级、事实未校验与重绑归属风险。
- 选定控制面明细恢复；数据面只运输，不增加业务欠费队列。
- 保留所有被修改 Requirement 的原 Scenario 名；跨仓镜像同名 change，收款明确继续 Mock。
- 任务覆盖崩溃、并发、部分成功、重放、归属、资金和模块边界；实现/安全审查均待执行。
- 严格校验结果在本轮 HANDOFF 登记；未通过运行验收前不得同步主规格或归档。

## 本轮验证（2026-10-02）

- 本件及本轮全部 7 件 change 的 OpenSpec 严格校验通过；对应 ingest-metering、wallet-ledger 的临时预合并规格严格校验通过，未写入主规格。
- MODIFIED 原 Requirement/Scenario 名称保留检查、局部文档链接、任务未勾选、文档变更范围及 `git diff --check` 均通过。
- 只读方案复核已完成，必要修正写回设计、规格和实施回归；收款继续 Mock。
- 本轮没有业务代码、依赖或迁移变更，未运行编译、业务测试、UI 实机或打包验收，不把提案通过记成实现完成。


## 实施审查(2026-10-02,实施轮)

- 红灯实证:WalletReplayAdjudicationTest(3 用例)与 WalletOuterTransactionLockTest 首跑呈现规格缺陷形态(重放被余额判断拦截、冲突伪装欠费、并发败方欠费化、外层事务锁提前释放致恢复读旧余额 0 → InsufficientFunds);UsageFactsAttributionTest 编译红先行。
- 实现后:后端 33→91 测试,`make check` 与 `mvn verify` EXIT 0(含 JaCoCo/PMD/SpotBugs/Spotless/Modulith + 前端五门禁 vitest 14)。
- 独立子代理全量 diff 复核(资金正确性/并发/恢复器有界性/规格符合性/H2-PG 方言,并实跑 4 个新增测试类 19/19 绿):**资金正确性核心(锁平衡与重入配对、afterCompletion 线程归属、AB-BA 死锁不可构造、幂等裁决序、非负、append-only、投影终态守卫、公平轮转 SQL)零真缺陷**。报 6 项处置:#1 归档时序(本轮归档动作收口);#2 DIVE 处理器吞异常成因 → 已补 `log.error`(留痕实证);#3 debit 参与外层事务时 markSuspended 随 doomed 事务静默失效的潜在隐患 → `markSuspended` 硬化 REQUIRES_NEW(余额不足是已提交资金状态的用户事实,不随外层业务回滚;clearIfSuspended 保持与入账同事务的规格硬约束不动);#4 native SQL `SELECT *` 违零容忍规则 → 已列名化;#5 `COALESCE(r.userId,:pinned)` 空参数路径零覆盖 → 已补 `unboundRow_redelivered_whileStillUnbound_keepsNullAttribution` 回归;#6 两 `@Scheduled` 共享单线程调度器 → README 演进登记(资金安全无影响,过期有懒守门)。修复后复验 91/91 绿。
- 主对话亲证:锁两条释放路径互斥配平、异常路径不泄漏、终态不降级写进 CASE WHEN 条件更新、事实比对覆盖全部 13 个镜像字段、恢复不计接收计数。
- 跨仓 e2e 实录:独立 muniu 实例(:8788,sink=ingest)+ 假上游——记录 ID 绑定→明文鉴权请求→200 接收欠费留存(cost 2,000,000 credit=0.002 元与定价分毫不差)→名单含 key→Mock 充值 10,000,000→恢复器自动补扣→余额 8,000,000 账实相符、投影 accepted、名单 v3→v4 收敛、再等一轮 attempts 稳定(缓冲结束无重投)、账本恰两笔分录(pay:+10000000 / usage:-2000000)。
