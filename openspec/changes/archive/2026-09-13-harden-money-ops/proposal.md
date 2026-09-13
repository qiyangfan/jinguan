# 资金操作加固:归属校验、操作人审计、管理员调账

## Why

三个涉钱面的正确性/可追责缺口,均为改动小、必须做,且都发生在资金路径上:

1. **订单归属校验缺失**:`GET /payments/orders/{orderId}` 不校验订单属于当前登录用户(PaymentService.query 无 userId 比对)——任何认证用户可探测他人订单与消费金额(水平越权)。mockPay 匿名则是合理形态(模拟 PSP 托管收银台,orderId 不可枚举 = capability URL),保留并写明定位。
2. **资金操作不记操作人**:admin credit 的账本行只有受益人,没记是谁操作的——涉钱操作不可追责。账本自身定位即"审计与对账的根基",操作人是分录语义的一部分。
3. **管理员只能加钱不能扣减**:错充无法修正(credit 强制 Positive,debit 未暴露管理端点)。one-api/new-api 均有管理员调账;最小补法是暴露 `WalletService.debit`(已存在),非负不变量不破——资金已被花掉时扣减失败,不允许负余额(核心不变量神圣)。

另有一个一行级安全疵:ingest token 用 `String.equals` 明文比对,换 `MessageDigest.isEqual` 恒时比较,消除时序侧信道。

复查(2026-09-12 亲证)补充一处并发语义缺陷:`insertOrReplay` 为先查后插,并发同 refId(PSP 并发重推回调/mock 订单无 @Version 双发布事件/muniu 并发重投)双方都查空后撞 `uq_ledger_ref`,DataIntegrityViolation 未被异常出口映射 → 裸 500——资金由唯一约束兜底不损坏,但违背「重投天然幂等」承诺,且 WalletConcurrencyTest 仅覆盖不同 refId 并发。

## What Changes

- 订单查询/本人列表端点加归属校验(非本人返回 404,不泄露存在性);新增 `GET /payments/orders` 本人订单分页(前端演进项已登记,前端订单簿目前仅存本地会话)。
- `ledger_entries` 加 `operator_user_id BIGINT NULL`(迁移 V4);MANUAL_TOPUP/MANUAL_ADJUST 分录必落操作人。
- 新端点 `POST /wallet/admin/debit`(ADMIN):reason=MANUAL_ADJUST,refId 幂等,复用 WalletService.debit(锁内完整事务 + 非负不变量)。
- ingest token 恒时比较。

## Capabilities

- `payment-orders`:REMOVED「mock 收银台匿名(现状)」+ ADDED「订单归属校验」「本人订单列表」
- `wallet-ledger`:ADDED「管理员调账」「资金操作审计」
- `ingest-metering`:MODIFIED「ingest 行契约」(恒时比较)

## Non-goals(防过度设计)

- 不做独立审计表(账本 append-only 兼作审计是既定纪律;非资金的管理操作留待真实需求);
- 不做 PSP 级退款(refund 走渠道原路,等真实 PSP adapter 一并实现);
- 不做负余额修正通道(非负不变量不破;资金已花掉的错充走线下流程);
- mockPay 不加认证(保留 capability URL 语义,与真实 PSP 收银台形态对齐)。

## Impact

- 迁移:V4(ledger_entries.operator_user_id,nullable,不回填历史);
- 模块:payment(归属校验+列表)、wallet(调账+操作人)、ingest(恒时比较),均为模块内改动,Modulith 门禁不动;
- 契约:openapi.json 随 delivery-pipeline change 统一导出,本 change 不单独处理前端类型。
