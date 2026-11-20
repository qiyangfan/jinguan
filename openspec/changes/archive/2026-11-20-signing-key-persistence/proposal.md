# 签名密钥持久化

## Why

`d19a395` 已把签名切到 RS256 并经 `/.well-known/jwks.json` 发布公钥,但签名密钥仍是进程内启动生成:重启即换钥,全部 access/refresh token 与 RP 缓存的 JWKS 同时失效。这是上线硬前提(归档 design D2 登记项)。

## What Changes

- 签名密钥改为挂载目录持久化:`active.pem`(PKCS#8 私钥)用于签发;`retired/*.pem`(退役公钥)仅发布与验签。
- kid 取 RFC 7638 公钥指纹(跨重启稳定);JWKS 发布 active + 退役全集;签发只用 active。
- 轮换 = 旧 `active.pem` 移入 `retired/`、新键就位、重启;退役公钥必须保留至其签发 token 全部过期(refresh TTL,默认 7 天)后方可移除。
- 未挂载目录时退化为进程内临时密钥并启动告警(仅限开发);挂载但缺 `active.pem`、RSA <2048 位、加密 PKCS#8、PKCS#1 一律启动失败。
- PEM 解析使用 JDK(PKCS#8/X.509),不引 BouncyCastle。

## Capabilities

### Modified Capabilities

- `auth-session`:签名密钥持久化、kid 稳定、退役公钥保留与轮换。

## Non-goals

- 不做热加载/在线轮换:轮换以重启生效(单实例形态)。
- 不建 `signing_keys` 表:私钥不入库,免库内加密与 KEK 管理。
- 不接 HSM/KMS;不做自动退休清理(保留期由运维执行,文档化)。
