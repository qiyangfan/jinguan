# 契约:锦官接入虎符 OIDC

## 方向

- 锦官是 RP;虎符是唯一 OIDC Provider。
- 锦官只消费标准端点(发现文档、授权、token、JWKS、end_session),无私有端点。
- 锦官不保存密码;`principals.subject` 必须等于虎符 `id_token.sub`。

## 端点

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/auth/oidc/enabled` | `{"enabled":bool}`;未配置时前端不展示入口 |
| GET | `/auth/oidc/start` | 生成 state/nonce/PKCE,302 到虎符 authorization_endpoint |
| GET | `/auth/oidc/callback` | 校验 state cookie + 单次 state + id_token,写 refresh cookie,302 到前端成功页 |
| GET | `/auth/logout/sso` | 吊销本地 refresh,302 到虎符 end_session(client_id + 登记回跳) |
| POST | `/auth/refresh` | refresh cookie → 新 access + 轮换 refresh |
| POST | `/auth/logout` | 吊销 refresh 家族并清 cookie |

## 校验

- 授权码 + `code_challenge_method=S256`;state 落库单次消费且绑定浏览器 cookie。
- `id_token` 的 `iss`、`aud`、`exp` 由 JWKS 验签与 validator 校验;`nonce` 必须等于本次登录值。
- 首登仅建 `USER`;角色/钱包/停服由锦官管理员本地授予;不接收产品授权 claim。
- access/refresh 为锦官自签 RS256,claims 含 `sub`(虎符 subject)、`username`、`role`、`typ`。

## break-glass

- `POST /auth/break-glass`,请求头 `X-Break-Glass-Token`;默认关闭。
- 成功返回短时 ADMIN access;写 `break_glass_audit`;不得创建普通账号。
