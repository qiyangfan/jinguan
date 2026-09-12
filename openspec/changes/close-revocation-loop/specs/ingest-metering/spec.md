## REMOVED Requirements

### Requirement: 欠费行为(现状)

**原因**:欠费仅跳过并计数、无任何停服联动,导致欠费用户可无限免费使用(资金闭环缺最后一环)。由「欠费停服联动」取代。

## ADDED Requirements

### Requirement: 欠费停服联动

余额不足的行仍必须 (MUST) 跳过并计 skippedInsufficient(零账本痕迹,充值后由 muniu 重投自然补账);同时必须 (MUST) 自动置位该用户欠费停服(独立小事务、幂等置位),使其名下全部 key 进入吊销下发名单——数据面在一个拉取周期(默认 60s)内停止放行。

#### Scenario: 闭环收敛
- **WHEN** 欠费用户的用量行到达
- **THEN** 行被跳过计数,用户进入停服,其 key 最迟下一拉取周期被数据面拒绝
