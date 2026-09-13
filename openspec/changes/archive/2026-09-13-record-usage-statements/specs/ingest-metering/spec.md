## REMOVED Requirements

### Requirement: 用量明细留存(现状)

**原因**:仅扣账不留存明细,用户无法对账,由「用量明细留存」「账单查询」取代。

## ADDED Requirements

### Requirement: 用量明细留存

ingest 收到的每一行必须 (MUST) 先于扣账语义幂等落库到 append-only `usage_records`(usage_id 全局唯一,镜像 UsageRow 关键字段 + 解析出的归属用户),并记录处理结果投影(final_outcome:accepted/replayed/skipped_unbound/skipped_free/skipped_insufficient);重投或充值后补账成功必须 (MUST) 更新投影与尝试计数——投影可更新,不属资金事实,账本 append-only 纪律不约束本表。

#### Scenario: 每行有解释
- **WHEN** 任意一行用量被处理(无论结果)
- **THEN** usage_records 中可查到该行:钱花在哪(accepted)、为什么没花(skipped_insufficient/unbound/free)、是否重投(attempts)

### Requirement: 账单查询

用户必须 (MUST) 能分页查询本人用量明细(新→旧);ADMIN 必须 (MUST) 能按用户查询;明细与账本流水可互相印证(USAGE 分录 ↔ accepted 明细行,经 refId="usage:{id}" 关联)。

#### Scenario: 争议对账
- **WHEN** 用户质疑一笔 USAGE 扣账
- **THEN** 经分录 refId 定位到明细行,核对模型/token/费用
