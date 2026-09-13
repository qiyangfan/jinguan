# 任务清单:资金操作加固

> 纪律:资金类改动先红后绿;收口 `make check` 全绿。

## 1. 订单归属

- [x] 1.1 PaymentService.query 加 userId 归属裁决:非本人订单抛 NotFound(404,不泄露存在性);负面测试:他人订单 404
- [x] 1.2 新增 `GET /payments/orders`(本人订单分页,新→旧;orderId 含时间前缀,字典序即时序);集成测试:仅返回本人订单
- [x] 1.3 前端 PaymentsPage 订单簿接服务端列表(useQuery + 变更后失效重取,替换本地会话 state);mock payUrl 流程回归
- [x] 1.4 (实现中发现的同源加固)支付状态迁移改原子条件 UPDATE `markPaidIfCreated`(订单行锁串行化并发回调),从根上消除并发同 refId 入账事件——连带修复 markPaid 读-改-写竞态

## 2. 操作人审计与管理员调账

- [x] 2.1 迁移 V4:ledger_entries.operator_user_id BIGINT NULL;实体/工厂扩展(禁 public setter 纪律)
- [x] 2.2 admin credit 落 operator_user_id(取 @AuthenticationPrincipal uid);admin 调账端点 `POST /wallet/admin/debit`(reason=MANUAL_ADJUST,refId 幂等,复用 WalletService.debit)
- [x] 2.3 测试:调账分录含操作人与负金额、余额不足时调账失败且零痕迹(422)、同 refId 幂等重放、USER 调用 403;账本流水 API 暴露 operator

## 3. 杂项安全

- [x] 3.1 ingest token 比对换 MessageDigest.isEqual 恒时比较(含测试不破坏既有 token 语义)
- [x] 3.2 幂等重放并发收敛:insertOrReplay 捕获唯一约束冲突后重读既有分录按重放返回;并发测试补「同 refId 双写恰一行生效、另一方 replayed=true、零异常」(concurrent_sameRefId_credits_replay_notFailure,先红后绿)

## 4. 收口

- [x] 4.1 WalletConcurrencyTest 回归(锁语义未被调账通道破坏);`make check` 全绿
