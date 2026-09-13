## RENAMED Requirements

- FROM: `### Requirement: 管理员手工入账(现状)`
- TO: `### Requirement: 管理员手工入账`

## MODIFIED Requirements

### Requirement: append-only 账本

账本只 (MUST) 提供 credit/debit 写入通道,任何代码路径禁止 (MUST NOT) UPDATE/DELETE `ledger_entries`;余额必须 (MUST) 由 SUM 派生,不物化。

#### Scenario: 余额即流水之和
- **WHEN** 查询任一用户余额
- **THEN** 返回值恒等于其全部账本分录 amount_credits 之和

### Requirement: 管理员手工入账

ADMIN 可经 `POST /wallet/admin/credit` 入账(reason=MANUAL_TOPUP),refId 缺省自动生成;分录必须 (MUST) 记录操作人(operator_user_id),与「资金操作审计」要求一致——「仅支持加钱、不记操作人」的旧现状已被「管理员调账」与「资金操作审计」取代。

#### Scenario: 手工兜底入账
- **WHEN** ADMIN 对 userId=1 入账 10,000,000,000 credit(=10 元)
- **THEN** 产生一行 MANUAL_TOPUP 分录(含操作人),余额增加,重复同 refId 调用幂等
