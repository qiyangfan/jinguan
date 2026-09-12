## Purpose

钱包账本——本服务唯一的领域核心。`ledger_entries` append-only,余额 = SUM(amount_micros),一切资金写入必经 `WalletService`。

## Requirements

### Requirement: append-only 账本

账本只 (MUST) 提供 credit/debit 写入通道,任何代码路径禁止 (MUST NOT) UPDATE/DELETE `ledger_entries`;余额必须 (MUST) 由 SUM 派生,不物化。

#### Scenario: 余额即流水之和
- **WHEN** 查询任一用户余额
- **THEN** 返回值恒等于其全部账本分录 amount_micros 之和

### Requirement: refId 幂等

写入必须 (MUST) 以 refId 全局唯一约束兜底:同键同载荷静默重放(返回既有分录,replayed=true),同键不同载荷必须 (MUST) 抛 IdempotencyConflict。

#### Scenario: 支付回调重推
- **WHEN** 同一订单的入账事件被重推(refId="pay:{orderId}")
- **THEN** 账本不产生第二行分录,余额不变

### Requirement: 非负不变量

扣账后余额不得 (MUST NOT) 为负:不足必须 (MUST) 抛 InsufficientFunds 且账本零痕迹;金额必须 (MUST) 为正,≤0 拒绝。

#### Scenario: 超扣拒绝
- **WHEN** 余额 10μ 时扣 11μ
- **THEN** 抛 InsufficientFunds,该用户账本无任何新分录

### Requirement: per-user 锁内含完整事务

并发写入必须 (MUST) 经 per-user ReentrantLock,锁的释放必须 (MUST) 晚于事务提交(锁内含完整编程式事务),杜绝锁外提交窗口读旧余额的超扣竞态。集群化需换账本行锁/分布式锁(已登记演进项)。

#### Scenario: 并发扣账不超扣
- **WHEN** 20 线程并发扣余额仅够 3 笔的账户
- **THEN** 恰 3 笔成功、17 笔拒绝、每笔恰好一种结局

### Requirement: 管理员手工入账(现状)

ADMIN 可经 `POST /wallet/admin/credit`(reason=MANUAL_TOPUP)入账,refId 缺省自动生成;当前该通道仅 (SHALL) 支持加钱,账本行不记录操作人。

#### Scenario: 手工兜底入账
- **WHEN** ADMIN 对 userId=1 入账 10,000,000μ
- **THEN** 产生一行 MANUAL_TOPUP 分录,余额增加,重复同 refId 调用幂等
