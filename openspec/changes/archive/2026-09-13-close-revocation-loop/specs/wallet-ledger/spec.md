## ADDED Requirements

### Requirement: 入账解除欠费停服

任何成功入账(支付入账/管理员手工入账)必须 (MUST) 在与账本分录相同的事务内清除该用户的欠费停服标记——恢复 choke point 在 WalletService.apply(一切入账必经之处),不可能出现「入账成功但停服未解除」。

#### Scenario: 充值恢复原子性
- **WHEN** 欠费停服用户的一笔支付入账事务提交
- **THEN** 账本分录与停服清除同时生效,下发名单在下一拉取周期移除其全部 key
