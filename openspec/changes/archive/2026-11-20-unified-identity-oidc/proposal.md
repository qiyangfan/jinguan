# 锦官身份加固(standalone)

> **范围收敛(2026-11-20 owner 裁决,HANDOFF 第 8 条)**:IdP 权威**不再内嵌锦官**,改为**独立项目**;三仓互联一律走 OIDC,各方只做 RP。锦官本仓**暂不做 IdP,也不做 RP 改造**(RP 对接待 IdP 立项后另登锦官 HANDOFF)。本件只剩 standalone 身份加固。
> 原「锦官内嵌 SAS 做 OIDC 权威」方案与 `identity-federation` 能力已移出本件,留给独立 IdP 项目。

## Why

锦官身份实现是三家最弱(实测),且钱包/账本安全直接依赖它:

- HS256 对称共享密钥,无 iss/aud/JWKS;对称体制下任何持 secret 方都能伪造任意 uid/role token。
- refresh 无服务端吊销:logout 仅清 cookie、轮换不废旧票。
- `suspended_at` 仅欠费停服、不阻断登录。
- 首用户 ADMIN 用 `users.count()==0` 无锁,并发可双 ADMIN。
- 默认密钥明文入库。

这些缺陷**与身份联邦无关**,standalone 也必须修;本件只做这些加固,不改变“谁签发对外身份”的归属。钱包锚定(`ledger_entries`/`mock_payment_orders`/`api_key_bindings` FK 直指 `users(id)`,uid BIGSERIAL、账本 append-only)保持不变。

## What Changes

- 签名 HS256→RS256 + iss/aud/kid;公钥经 `/.well-known/jwks.json` 发布(内部验签/审计用,是否供外部 RP 消费由独立 IdP 立项决定)。
- refresh 落库轮换 + 重用检测 + 真吊销(logout/改密踢 token)。
- 账号禁用与欠费停服分离:新增 disabled 状态,login/refresh 闸门;`suspended_at` 语义不变。
- 首注册自动 ADMIN 保留为 standalone 默认,仅做并发原子化加固(不改变可见语义)。

## Capabilities

### Modified Capabilities

- `auth-session`:RS256 签名、refresh 轮换吊销、账号禁用闸门、ADMIN 并发加固。

## Non-goals

- 不做 OIDC IdP / 授权码 + PKCE / 跨域 SSO(移至独立 IdP 项目)。
- 不做锦官 RP 改造(待 IdP 立项,另行登记)。
- 不上收孔明/木牛认证,不迁移其登录入口。
- 不废首注册自动 ADMIN;不做全局 `sub` 替换;密码哈希保持 BCrypt(Argon2 可选加固)。
- 不接真实 PSP;不引消息队列/第二套账本/分布式事务;mk2- 平台 key 另列。

## 待重裁决(不属本件)

独立 IdP 的用户存储与锦官账本/users 的锚定关系(HANDOFF 第 8 条):身份源归 IdP,还是仍以锦官 `users` 为源。本件不依赖该裁决——加固对两种走向都成立。
