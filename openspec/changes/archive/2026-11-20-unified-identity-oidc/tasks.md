# 任务:锦官身份加固(standalone)

> 资金/契约类先红后绿;勾选以实测为准,未验证不勾。全量 `make check` 尚未跑,已跑项在条目内注明。
> IdP/授权码/RP 改造已移出本件(HANDOFF 2026-11-20 第 8 条),不在此列。

## 1. 签名层

- [x] 1.1 先写失败测试:RS256 签发/验签、JWKS 端点返回公钥、iss/aud/kid claims
- [x] 1.2 HS256→RS256 + JWK source + kid(已落地;密钥持久化与退役轮换未做,见 D2)
- [x] 1.3 JWKS 端点 `/.well-known/jwks.json` + SecurityConfig 白名单
- [x] 1.4 短窗双算法过渡:**取消**(2026-11-20)——换钥即全体失效,双算法窗口会保留可伪造的对称面,且旧 secret env 契约已删除;若存在生产 HMAC 存量,须在部署窗口内另立兼容迁移
- [x] 1.5 `make check` 全绿(2026-11-20 实测 exit 0:123 测试 0 失败、真 PG 6/6)

## 2. refresh 加固(先红后绿)

- [x] 2.1 先写失败测试:旧 refresh 轮换后失效、重放吊销全家族、logout/改密踢 token
- [x] 2.2 `refresh_sessions` 迁移(只增)+ 家族语义原子消费/吊销(D3)
- [x] 2.3 logout 真吊销、改密连带吊销;补 AuthSessionIntegrationTest 断言
- [x] 2.4 `make check` 全绿(2026-11-20 实测 exit 0,含轮换/宽限用例)
- [x] 2.5 并发续期宽限:30 秒内同票重复提交返回同一后继票,超窗吊销全家族(`RefreshRotationIntegrationTest` 并发/宽限用例与 `RefreshReuseWindowIntegrationTest` 超窗用例实测通过,Flyway V12)

## 3. 账号状态分离(先红后绿)

- [x] 3.1 先写失败测试:disabled 用户 login/refresh 被拒;suspended_at 仍不阻断登录
- [x] 3.2 `users.disabled` 迁移(只增)+ login/refresh 闸门(D4)
- [x] 3.3 `make check` 全绿(2026-11-20 实测 exit 0,含禁用闸门用例)

## 4. ADMIN 引导加固

- [x] 4.1 保留首注册自动 ADMIN,单行 `identity_bootstrap` 护栏行写锁原子化(Flyway V13,可见语义不变)
- [x] 4.2 并发首注册不双 ADMIN 测试(`FirstAdminAtomicityTest`,并发用例连跑 3 次通过)
- [x] 4.3 `make check` 全绿(2026-11-20 实测 exit 0,含并发首注册用例)

## 5. 收口

- [x] 5.1 README「演进登记」同步(standalone 加固已落地;密钥持久化与 IdP 立项已登记)
- [x] 5.2 聚合层 HANDOFF 协调条目回写(2026-11-20)
- [x] 5.3 `make check` 全绿 + 归档(2026-11-20)
