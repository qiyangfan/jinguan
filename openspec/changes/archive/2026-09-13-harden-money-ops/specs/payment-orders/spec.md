## REMOVED Requirements

### Requirement: mock 收银台匿名(现状)

**原因**:现状条目包含"订单查询不校验归属"的缺口描述,由「订单归属校验」「本人订单列表」取代;mock 收银台匿名本身保留并移入归属校验条目定位说明。

## ADDED Requirements

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
