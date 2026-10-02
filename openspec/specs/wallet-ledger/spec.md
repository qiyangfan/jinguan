## Purpose

钱包账本——本服务唯一的领域核心。`ledger_entries` append-only,余额 = SUM(amount_credits),一切资金写入必经 `WalletService`。

## Requirements

### Requirement: append-only 账本

账本只 (MUST) 提供 credit/debit 写入通道,任何代码路径禁止 (MUST NOT) UPDATE/DELETE `ledger_entries`;余额必须 (MUST) 由 SUM 派生,不物化。

#### Scenario: 余额即流水之和
- **WHEN** 查询任一用户余额
- **THEN** 返回值恒等于其全部账本分录 amount_credits 之和

### Requirement: refId 幂等

写入 SHALL 以 refId 全局唯一约束兜底:同键同用户、金额与原因返回既有分录并标记重放,同键不同载荷 SHALL 抛幂等冲突。并发同键 SHALL 收敛为相同重放语义,不以裸数据完整性错误替代。已有分录的重放或冲突 SHALL 先于新扣款余额判断;合法重放 SHALL NOT 因当前余额不足置位业务停服。新扣款仍遵守非负不变量。

#### Scenario: 支付回调重推

- **WHEN** 同一订单入账事件被重推
- **THEN** 账本不产生第二行分录,余额不变

#### Scenario: 并发同键重放

- **WHEN** 同一 refId 的写入并发到达
- **THEN** 恰一行分录生效,其他请求返回既有结果,不收到裸 500

#### Scenario: 低余额下已扣分录重放

- **GIVEN** 用量扣款已成功,当前余额低于该笔金额
- **WHEN** 同用户同载荷的 refId 再次到达
- **THEN** 返回重放,余额和业务停服状态不改变

#### Scenario: 低余额下冲突仍为冲突

- **GIVEN** refId 已存在且当前余额不足
- **WHEN** 同 refId 携带不同用户、金额或原因
- **THEN** 报幂等冲突,不伪装成新欠费,不新增分录

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

任何成功入账 SHALL 与分录在同一事务内清除用户的欠费停服标记。没有待结算用量时,既有充值恢复语义 SHALL 保持。有历史待结算时,入账提交后 SHALL 由统一恢复流程继续结算;充值不足造成的新扣款拒绝 SHALL 可重新置位业务停服,并沿既有名单周期收敛。支付入账 SHALL 继续来自 Mock 通道,本件不要求真实收款渠道。

#### Scenario: 充值恢复原子性

- **GIVEN** 用户没有尚需恢复的待结算用量
- **WHEN** 欠费停服用户的一笔支付入账事务提交
- **THEN** 账本分录与停服清除同时生效,名单下一拉取周期移除其全部 key

#### Scenario: 入账后待结算仍不足

- **GIVEN** 用户存在历史待结算用量
- **WHEN** 入账提交后,恢复扣款再次判断余额不足
- **THEN** 已提交入账保留,余额不为负,剩余用量保留并重新收敛业务停服

### Requirement: 资金互斥覆盖物理事务完成

同用户资金修改及其停服决定 SHALL 串行执行;资金写入参与调用方事务时,该互斥 SHALL 持续至拥有该写入的物理事务提交或回滚完成,SHALL NOT 在内部资金方法返回时提前释放。支付状态、入账分录及欠费停服清除 SHALL 保持既有原子提交,恢复结算 SHALL 基于已经提交的资金状态判断。

#### Scenario: Mock 充值与恢复并发

- **GIVEN** 用户的 Mock 支付入账参与尚未提交的支付事务,恢复器同时处理该用户待结算用量
- **WHEN** 该支付事务提交
- **THEN** 恢复器在提交后余额上判断扣款,不因读取充值前余额而在充值提交后错误重新停服
- **AND** 分录、余额与停服结果一致

#### Scenario: 外层回滚释放互斥

- **GIVEN** 支付入账参与外层事务
- **WHEN** 外层事务回滚
- **THEN** 未提交的入账与停服清除均不生效,互斥正常释放,后续资金操作可继续
