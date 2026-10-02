# 实施任务

每项不超过两小时;资金与契约测试先红后绿,每阶段以 `make check` 收口。

## 1. 资金与事实不变量

- [x] 1.1 补低余额同 refId 重放、不同载荷冲突、并发同键的失败测试。
  → `WalletReplayAdjudicationTest` 3 用例首跑全红(重放被余额拦截/冲突伪装欠费/并发败方 0 重放),修复后绿。
- [x] 1.2 将钱包重放/冲突裁决放到新扣款余额判断前,保留非负余额与停服语义。
  → `WalletService.debitLocked` 重构:先 `findByRefId` 裁决(重放/冲突),无既有分录才查余额;`adjudicate` 抽取复用。
- [x] 1.2a 先补 Mock 外层支付提交/回滚与恢复并发测试;修正外层事务提前解锁,同时保持无外层时扣款拒绝与独立停服事务仍在锁内。
  → `WalletOuterTransactionLockTest` 红灯实证(恢复线程读充值前余额 0 → InsufficientFunds);`withUserLock` 参与外层事务时挂 afterCompletion 持有到物理提交/回滚,回滚用例与无外层停服用例同绿。
- [x] 1.3 补用量同 ID 不同计费事实、key 重绑、accepted/replayed 不降级的失败测试。
  → `UsageFactsAttributionTest.sameId_differentBillingFacts_rejected_beforeFunds` / `keyRebind_historicalRow_staysWithOriginalUser` / `settledProjection_neverDowngraded`(编译红 → 实现后绿)。
- [x] 1.4 实现事实校验、首次归属固定与投影并发保护,禁止改写原始事实。
  → `UsageRecordEntity.billingFactsEqual`(13 镜像字段);`bumpAttemptAndTransition` 以 `COALESCE(r.userId,:pinned)` 只允许首次落位;终态不降级写进 CASE WHEN 条件更新(SQL 内裁决,天然防并发倒退)。
- [x] 1.5 运行相关测试及 `make check`,保存证据。
  → `make check` EXIT 0(后端 90 测试含 JaCoCo/PMD/SpotBugs/Spotless/Modulith + 前端五门禁 vitest 14)。

## 2. 可靠接收与恢复

- [x] 2.1 补明细预留失败不回 2xx、预留后中断、扣账后投影失败的回归用例。
  → `reserveFailure_isNon2xx_andLeavesNoFundsEntry`(65 字符 ID 违约束 → 500,零分录)/`crashAfterReserve_pendingRow_recoveredOnDemand`/`crashAfterDebit_projectionRepaired_withoutSecondDebit`(重放修复投影,余额不变)。
- [x] 2.2 实现先可靠预留再扣款及 pending 投影,尝试计数单次递增;内部结算尝试时间供用户轮转,必要变更使用新 Flyway 文件。
  → `IngestService.accept` 每行 reserve→debit→投影;`UsageRecordService.reserve`(DIVE 就地重试);V8 迁移(`last_settlement_attempt_at` 列 + 候选/轮转双索引);预留与结算结果落投影分离不双计。
- [x] 2.3 补有界扫描、用户间公平(含本轮触及行全部成功但仍有未触及 pending,不得重新首次优先)、同用户顺序、在线 ingest 与扫描并发及重启恢复测试。
  → `UsageSettlementRecoveryTest.userFairness_exitedRowsKeepRotationHistory`(3 用户×3 行 caps 2×2 两轮)/`candidateOrdering_neverAttemptedFirst_thenRecentAttemptLast`(查询级钉子)/`sameUser_firstReceiveOrder_stopAtInsufficient`/`onlineIngest_vsRecovery_concurrent_sameRefIdConverges`/收敛后空轮(等价重启再扫)。
- [x] 2.4 实现单一周期恢复器;只处理已验证且固定归属的 pending/欠费候选,不自动追收未绑定历史。
  → `UsageSettlementScheduler`(fixedDelay 不重叠,`recoverOnce` 可驱动入口;`unboundAndFreeHistory_neverAutoCharged` 用例背书)。
- [x] 2.5 覆盖 Mock 支付、管理员入账、充值仍不足与既有吊销名单收敛;扫描失败保留候选。
  → `mockPayment_thenRecovery_settlesHistory_andKeepsUnsuspended`(下单→回调→恢复→不停服)/`sameUser_...stopAtInsufficient`(部分充值重新停服);名单收敛跨仓实证见 3.3。
- [x] 2.6 运行相关测试及 `make check`,含 Modulith、资金覆盖与静态分析门禁。
  → `mvn verify` EXIT 0(ModulithVerificationTest 绿;SpotBugs 初拦 CT_CONSTRUCTOR_THROW 已改 @PostConstruct 校验)。

## 3. 呈现与跨仓交付

- [x] 3.1 在现有用量页呈现待结算/恢复结果;后台恢复不重复计 ingest 接收指标。
  → `WalletPage` OUTCOME_META 增 `pending`(待结算)徽章 + 卡片说明「充值后由系统自动补扣」;`recovery_doesNotFakeIngestCounts` 断言恢复轮 INGEST_ROWS 三桶零增量、wallet.operations 正常承载。
- [x] 3.2 更新并导出 OpenAPI/前端生成类型及两仓契约登记,确认 UsageRow 和 IngestResult 形态不漂移。
  → 真机 `/v3/api-docs` ↔ 入库 `openapi.json` 24↔24 路径、24↔24 schemas 深度相等(除 servers 探针差异),`IngestResult` 六计数与 UsageRow 20 字段不变;`contracts/control-plane.md` 管子 3 登记接收/结算责任分离;前端 `usage-api.ts` 注释补 pending。
- [x] 3.3 与木牛配对 change 做"200 接收→缓冲结束→Mock 充值→后台结算"的端到端验证,记录账本与明细一致性。
  → 独立 muniu 实例(:8788,sink=ingest)+ 假上游:CLI 签发 key(记录 ID 01a0fa5d-abba-…)绑定→明文鉴权请求→锦官 200 接收欠费留存(0.002 元分毫不差)→名单含 key→Mock 充值 0.01 元→恢复器补扣→余额 800 万 credits 账实相符、投影 accepted、名单 v3→v4 收敛、再等一轮 attempts 稳定(缓冲结束无重投)、账本恰两笔分录(pay:+10000000 / usage:-2000000)。
- [x] 3.4 完成相关前端门禁、亮暗两态与长金额状态走查;运行 `make check`。
  → 五门禁绿(typecheck/lint/stylelint/prettier/vitest 14);浏览器走查 1280/768/375 × 亮暗六态 scrollWidth=视口宽零溢出,恢复补扣后流水/徽章/长金额正常。
- [x] 3.5 完成内置代码与资金安全审查,主对话核验崩溃、并发、归属和非负不变量。
  → 独立子代理全量 diff 审查(见 review 记录);主对话亲证锁重入平衡、异常路径释放、投影条件更新竞态、事实比对覆盖全部镜像字段。
- [x] 3.6 写回 README/HANDOFF/契约表;双方提案验收通过后同步各自主规格并归档,收款继续 Mock。
  → README 待结算恢复节 + application.yml `jinguan.settlement` 配置四项 + HANDOFF 双仓互引;delta 合并主 specs 后归档 `archive/2026-10-02-recover-pending-usage-settlement`。
