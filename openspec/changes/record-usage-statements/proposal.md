# 用量明细留存与账单查询

## Why

ingest 收到的每一行用量数据当前只换算成一笔 USAGE 扣账,行本身被丢弃——用户在控制面只能看到"扣了多少钱",看不到"哪一次调用、哪个模型、多少 token、为什么这行没扣"(争议对账无依据)。消费日志/用量明细是 one-api/new-api 系产品的核心卖点,也是"钱包流水"与"消费明细"两级账单的自然分工:账本管钱(权威),明细管事(解释钱)。

设计原则:**明细先行落库,解释每一笔钱**——muniu 的 usage_logs 是全量事实源,锦官只需镜像 ingest 实际收到的行并记录归账结果,不做任何分析聚合。

## What Changes

- 新表 `usage_records`(迁移 V5,append-only 为主):镜像 UsageRow 关键字段(usage_id 全局唯一、key_id、tenant_id、model_alias/upstream_model、status/error_class、四类 token 数、cost_micro、currency、created_at)+ 归账投影(user_id,解析时的归属)+ 处理结果(final_outcome: accepted/replayed/skipped_unbound/skipped_free/skipped_insufficient + attempts 计数)。
- ingest 链路:每行**先**落明细(幂等,已有 usage_id 跳过插入),**后**走既有扣账/跳过语义;重投/补账成功时更新 final_outcome(attempts+1)——final_outcome 是投影不是资金事实,允许更新,账本 append-only 纪律不约束此表。
- 查询:`GET /wallet/usage`(本人分页,新→旧)+ `GET /admin/usage?userId=`(ADMIN);前端 WalletPage 增「用量明细」视图。

## Capabilities

- `ingest-metering`:REMOVED「用量明细留存(现状)」+ ADDED「用量明细留存」「账单查询」

## Non-goals(防过度设计)

- 不做聚合报表/仪表盘(读侧需要时再基于本表加,不在 ingest 链路做);
- 不做明细保留策略/分区(量级未到;登记触发条件:行数过亿或表膨胀影响 ingest 写入);
- 不做实时推送/告警(欠费已有停服联动,余额预警随真实需求另排);
- 不回填历史行(变更前无明细可补,账本流水本身完整)。

## Impact

- 迁移:V5(usage_records;列类型维持 H2 兼容纪律);
- 模块:ingest(落库+查询端点,域内改动);钱包流水页前端增明细视图;
- 契约:UsageRow PascalCase 镜像字段与 muniu 逐字段对齐,漂移即静默丢字段的警告注释随实体保留。
