# 设计:锦官身份加固(standalone)

## 上下文

锦官 Boot 4.1 / Security 7.1 / Modulith 2.1。现状单条 filterChain + 手写 `JwtAuthFilter` + 已改 RS256 `JwtService` + SPA 回退 + 匿名机器面。

**IdP 已移出本仓**(HANDOFF 2026-11-20 第 8 条:IdP 立项为独立项目,三仓只做 RP,锦官暂不动)。本设计只覆盖与联邦无关的 standalone 加固;不做授权服务器、不做授权码流程、不做锦官 RP 改造。

约束:钱包 uid 锚定不可重排、账本 append-only、Modulith 门禁、Flyway 只增不改、资金/契约先红后绿。

## 决策

### D1 — 签名层

HS256 → RS256 非对称签名;claims 含 iss/aud/uid/role/typ;公钥经 `/.well-known/jwks.json` 发布。对称共享密钥可被任何持密钥方伪造,故弃用。JWKS 供内部验签/审计;是否作为对外联邦契约由独立 IdP 立项决定。

### D2 — 密钥管理

RS256 KeyPair;JWK source 暴露公钥(kid 标识)。**当前为进程内启动生成(临时)**,生产前须改为挂载密钥文件(部署注入)或库表 `signing_keys`(Flyway 只增),并支持退役公钥保留至其签发 token 全部过期。私钥不落明文配置。废弃 `JINGUAN_JWT_SECRET` 对称 env 契约。

### D3 — refresh 轮换 + 重用检测(抬孔明范式)

活跃票 + 轮换墓碑 + 家族吊销。已落库 `refresh_sessions`(jti/uid/expires/consumed,Flyway V11);refresh 原子消费旧票,重放即吊销全家族;logout/改密吊销该用户全部家族。补 `AuthSessionIntegrationTest` 断言旧票失效。
并发宽限:墓碑记录轮换时刻与后继票链接(`rotated_at`/`replaced_jti`,Flyway V12);同票在宽限窗口(默认 30 秒,`jinguan.security.refresh-grace-seconds`)内重复提交,沿链接重签仍活跃的后继票(jti 不变),避免多标签页并发续期互踢;后继已吊销(登出/改密)或链断时不复活会话、吊销全家族;超窗仍按重放吊销全家族。

### D4 — 账号禁用 ≠ 欠费停服

新增 `users.disabled`(Flyway V10,只增);login/refresh 闸门查 disabled。`suspended_at` 欠费停服语义不变(仍不阻断登录,只切数据面用量)。两者分离,文档化。

### D5 — 首注册 ADMIN:保留 + 原子化

**保留** `count()==0` 首注册自动 ADMIN 的可见语义(standalone 默认);以单行 `identity_bootstrap` 护栏行写锁(Flyway V13)串行化并发首注册,消除并发双 ADMIN。种子管理员仅作为可选引导方式,不替换默认路径。

### D6 — 主体语义不变

access token 保持现有 claim 语义(`sub=username` + 恒含 `uid`);不做全局 `sub` 替换。后续若锦官成为 RP,以 IdP 的 `sub` 建本地影子映射,不在本件。

## 待验证

- 锦官存量:`SELECT role,count(*) FROM users GROUP BY role` + api_key_bindings/key_revocations 计数(起库后)。
- 是否仅 dev 一套环境(生产存量)。

## 风险与回滚

- 签名切换瞬间所有已发 HMAC token 失效 → 需短窗双算法过渡(尚未实现),窗口越短越好(窗口期 secret 泄露仍可伪造)。
- 当前签名密钥为进程内临时生成:重启即换钥,JWKS 与已发 token 同时失效;生产前必须落持久化(D2)。
- refresh 重用检测含 30 秒宽限:被盗旧票若在轮换后 30 秒内重放,可换得同一后继会话;这是并发续期与防重放之间的既定折衷,窗口可按 `refresh-grace-seconds` 收紧。
- 回滚:全部为本仓内建加固,回退本仓提交即可,不涉及其他仓。
