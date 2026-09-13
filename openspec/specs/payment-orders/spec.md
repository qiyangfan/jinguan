## Purpose

支付单状态机(CREATED → PAID | EXPIRED)+ `PaymentProvider` 出站端口(契约已冻结,真实 PSP = 未来一个 adapter)。当前唯一实现为 mock 渠道。

## Requirements

### Requirement: 订单创建

订单必须 (MUST) 由服务端生成 orderId(`po-` + 时间前缀随机串,不可枚举),金额必须为正;渠道策略执行下单返回 CreatedOrder(providerRef/payUrl/expiresAt)。

#### Scenario: mock 下单
- **WHEN** 认证用户创建 provider=mock 的订单
- **THEN** 返回 orderId 与 payUrl,订单落库状态 CREATED

### Requirement: 回调守门与幂等

回调必须 (MUST) 先经渠道适配器验签;过期订单必须 (MUST) 拒绝置 PAID(懒过期守门,资金正确性不依赖扫描调度);已 PAID 订单重推回调必须 (MUST) 幂等返回,仅首次状态转换发布 PaymentPaidEvent。

#### Scenario: 过期订单迟到回调
- **WHEN** 超过 order-ttl 后回调到达
- **THEN** 返回 409 OrderExpired,订单状态与账本均不变

### Requirement: 订单超时双保险

过期收敛必须 (MUST) 双保险:L1 懒过期守门(见上);L2 游标批量扫描调度(`@Scheduled` fixedDelay 可配,默认 30s,keyset 分页,仅收敛状态及时性)。

#### Scenario: 扫描收敛
- **WHEN** CREATED 订单超过 TTL
- **THEN** 下一轮扫描将其置 EXPIRED,积压可多轮消化

### Requirement: 支付入账同事务强一致

PaymentPaidEvent 必须 (MUST) 以 BEFORE_COMMIT 同事务监听消费,credit(refId="pay:{orderId}") 与订单状态变更原子提交;规模化后切换持久化事件(已登记演进项)。

#### Scenario: 支付闭环
- **WHEN** mock 收银台支付成功回调
- **THEN** 订单置 PAID 且账本新增 PAYMENT_TOPUP 分录,同事务生效

### Requirement: 订单归属校验

订单查询等认证端点必须 (MUST) 校验订单属于当前登录用户,非本人订单必须 (MUST) 返回 404(不泄露存在性);`POST /payments/mock/{orderId}/pay` 保持匿名——其定位是模拟 PSP 托管收银台,安全性等同真实场景下 payUrl 的持有即授权(orderId 服务端生成、不可枚举)。

#### Scenario: 水平越权拒绝
- **WHEN** 用户 A 携带自身 token 查询用户 B 的订单
- **THEN** 返回 404,A 无法探测 B 的订单与消费金额

### Requirement: 本人订单列表

认证用户必须 (MUST) 能分页获取本人订单列表(新→旧);服务端列表是订单簿唯一事实源(前端不再本地暂存)。

#### Scenario: 订单簿还原
- **WHEN** 用户更换浏览器后打开支付页
- **THEN** 历史订单完整呈现,金额/状态与账本一致
