# control-plane-contracts Specification

## Purpose
TBD - created by archiving change control-plane-contract-pinning. Update Purpose after archive.

## Requirements

### Requirement: 契约登记单源

四根跨仓管子(价目下发/key 吊销快照/用量上报/拓扑下发)必须 (MUST) 在 `contracts/control-plane.md` 逐根登记:端点、方向、形态权威方、字段清单、金额单位、双端实现锚点、当前状态;`/control/v1/*` 三管子的形态权威为本仓 `openapi.json` 对应路径(CI 漂移门禁背书),ingest 行形态权威为 muniu `UsageRow` JSON 序列化(产生方为准)。登记表是跨仓契约问答的唯一事实源。

#### Scenario: 契约问答可单源回答
- **WHEN** 任一仓要变更某管子的字段或形态
- **THEN** 从登记表直接读出:权威在哪、另一端实现锚点在哪、需同步什么

### Requirement: 跨仓变更纪律机制化

管子字段/形态的任何变更必须 (MUST) 双方 change 同步(AGENTS.md 既有纪律)并在两仓 HANDOFF 互引留痕;聚合层一致性脚本(`contract-consistency.sh`)报红的字段级漂移必须 (MUST) 在任一侧归档前修复——脚本红是人工巡检门,不自动阻断构建。

#### Scenario: 字段改名漂移被抓获
- **WHEN** 一侧将某管子字段改名,另一侧未同步
- **THEN** 脚本比对该侧源码字段清单 vs 登记表/契约清单,报红并列出差异字段

### Requirement: ingest 行形态显式化

用量上报行的字段清单(PascalCase:ID/APIKeyID/TenantID/Model/Alias/PromptTokens/CompletionTokens/ReasoningTokens/CachedTokens/Estimated/CostCredits/Currency/RequestID/AttemptIndex/Status/首 token 与总时长等,以登记表为准)必须 (MUST) 显式列入契约登记表;清单变更必须 (MUST) 遵循跨仓变更纪律,禁止 (MUST NOT) 以「序列化恰好如此」的方式隐式演化。

#### Scenario: 新增计量字段
- **WHEN** muniu 侧 UsageRow 新增一个计量字段并随 ingest 推送
- **THEN** 登记表先经双方 change 更新,锦官 IngestUsageRow 镜像同步,脚本双绿后归档
