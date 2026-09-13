## MODIFIED Requirements

### Requirement: 归账与幂等

行必须 (MUST) 经 api_key_bindings 按 APIKeyID 归账到用户;未绑定行跳过(skippedUnbound);Status 非 ok 或 CostCredits≤0 的行跳过(skippedFree);扣账 refId = "usage:{UsageRow.ID}",重投幂等计 replayed 不重复扣钱。

#### Scenario: 重投幂等
- **WHEN** 同一批行被 muniu 重投
- **THEN** 已扣行计 replayed,余额不变
