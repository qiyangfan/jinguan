## MODIFIED Requirements

### Requirement: refId 幂等

写入必须 (MUST) 以 refId 全局唯一约束兜底:同键同载荷静默重放(返回既有分录,replayed=true),同键不同载荷必须 (MUST) 抛 IdempotencyConflict;**并发同键写入必须 (MUST) 收敛为同一重放语义**——唯一约束冲突时必须 (MUST) 重读既有分录按幂等重放处理,禁止 (MUST NOT) 以裸 500(数据完整性违例)替代重放。资金不变量与非负裁决不受影响。

#### Scenario: 支付回调重推
- **WHEN** 同一订单的入账事件被重推(refId="pay:{orderId}")
- **THEN** 账本不产生第二行分录,余额不变

#### Scenario: 并发同键重放
- **WHEN** 同一 refId 的写入并发到达(如 PSP 并发重推同一回调、muniu 并发重投同一计量行)
- **THEN** 恰一行分录生效,另一请求静默重放返回既有结果;任何一方不得收到 500

## ADDED Requirements

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
