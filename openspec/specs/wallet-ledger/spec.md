## Purpose

钱包账本——本服务唯一的领域核心。`ledger_entries` append-only,余额 = SUM(amount_credits),一切资金写入必经 `WalletService`。

## Requirements

### Requirement: append-only 账本

账本只 (MUST) 提供 credit/debit 写入通道,任何代码路径禁止 (MUST NOT) UPDATE/DELETE `ledger_entries`;余额必须 (MUST) 由 SUM 派生,不物化。

#### Scenario: 余额即流水之和
- **WHEN** 查询任一用户余额
- **THEN** 返回值恒等于其全部账本分录 amount_credits 之和

### Requirement: refId 幂等

写入必须 (MUST) 以 refId 全局唯一约束兜底:同键同载荷静默重放(返回既有分录,replayed=true),同键不同载荷必须 (MUST) 抛 IdempotencyConflict;**并发同键写入必须 (MUST) 收敛为同一重放语义**——唯一约束冲突时必须 (MUST) 重读既有分录按幂等重放处理,禁止 (MUST NOT) 以裸 500(数据完整性违例)替代重放。资金不变量与非负裁决不受影响。

#### Scenario: 支付回调重推
- **WHEN** 同一订单的入账事件被重推(refId="pay:{orderId}")
- **THEN** 账本不产生第二行分录,余额不变

#### Scenario: 并发同键重放
- **WHEN** 同一 refId 的写入并发到达(如 PSP 并发重推同一回调、muniu 并发重投同一计量行)
- **THEN** 恰一行分录生效,另一请求静默重放返回既有结果;任何一方不得收到 500

### Requirement: 非负不变量

扣账后余额不得 (MUST NOT) 为负:不足必须 (MUST) 抛 InsufficientFunds 且账本零痕迹;金额必须 (MUST) 为正,≤0 拒绝。

#### Scenario: 超扣拒绝
- **WHEN** 余额 10,000,000,000 credit(=10 元)时扣 11,000,000,000 credit(=11 元)
- **THEN** 抛 InsufficientFunds,该用户账本无任何新分录

### Requirement: per-user 锁内含完整事务

并发写入必须 (MUST) 经 per-user ReentrantLock,锁的释放必须 (MUST) 晚于事务提交(锁内含完整编程式事务),杜绝锁外提交窗口读旧余额的超扣竞态。集群化需换账本行锁/分布式锁(已登记演进项)。

#### Scenario: 并发扣账不超扣
- **WHEN** 20 线程并发扣余额仅够 3 笔的账户
- **THEN** 恰 3 笔成功、17 笔拒绝、每笔恰好一种结局

### Requirement: 管理员手工入账

ADMIN 可经 `POST /wallet/admin/credit` 入账(reason=MANUAL_TOPUP),refId 缺省自动生成;分录必须 (MUST) 记录操作人(operator_user_id),与「资金操作审计」要求一致——「仅支持加钱、不记操作人」的旧现状已被「管理员调账」与「资金操作审计」取代。

#### Scenario: 手工兜底入账
- **WHEN** ADMIN 对 userId=1 入账 10,000,000,000 credit(=10 元)
- **THEN** 产生一行 MANUAL_TOPUP 分录(含操作人),余额增加,重复同 refId 调用幂等

### Requirement: 管理员调账

ADMIN 必须 (MUST) 能经 `POST /wallet/admin/debit` 扣减用户余额(reason=MANUAL_ADJUST,refId 幂等);调账必须 (MUST) 复用 WalletService.debit 的全部不变量(per-user 锁内完整事务、非负裁决)——余额不足时调账失败且账本零痕迹,负余额被禁止 (MUST NOT)。

#### Scenario: 错充修正
- **WHEN** ADMIN 对错充用户调账扣回,且该用户余额充足
- **THEN** 产生 MANUAL_ADJUST 负分录,余额减少;重复同 refId 调用幂等

#### Scenario: 资金已花掉
- **WHEN** ADMIN 对余额不足的用户调账扣回
- **THEN** 拒绝且零痕迹(核心非负不变量不破,不设负余额通道)

### Requirement: 资金操作审计

管理员发起的资金分录(MANUAL_TOPUP/MANUAL_ADJUST)必须 (MUST) 记录操作人(operator_user_id);账本 append-only 纪律不变(新增列为可空列,历史行不回填)。

#### Scenario: 追责
- **WHEN** 排查一笔手工资金分录
- **THEN** 能从分录直接读出发起操作的 ADMIN

### Requirement: 入账解除欠费停服

任何成功入账(支付入账/管理员手工入账)必须 (MUST) 在与账本分录相同的事务内清除该用户的欠费停服标记——恢复 choke point 在 WalletService.apply(一切入账必经之处),不可能出现「入账成功但停服未解除」。

#### Scenario: 充值恢复原子性
- **WHEN** 欠费停服用户的一笔支付入账事务提交
- **THEN** 账本分录与停服清除同时生效,下发名单在下一拉取周期移除其全部 key
