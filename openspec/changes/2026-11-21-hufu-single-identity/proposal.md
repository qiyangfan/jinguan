# 提案:锦官接入虎符单一身份(去本地账号)

## Why

全 portfolio 已定稿只有虎符一套人类账户(ADR-0010)。锦官当前 `users` 保存密码、
提供注册/登录/改密,并以 `users.id BIGSERIAL` 锚定账本/订单/API key/refresh 会话;
这与「一套账户」冲突,也保留了两套凭据与禁用语义。锦官需要彻底改为按虎符
`sub` 组织授权数据,并作为标准 OIDC RP 登录。

## What Changes

- `users` 重建为 `principals(subject PK, username, role, disabled, suspended_at, created_at)`;
  删除密码与本地用户 ID。
- 账本/订单/API key/refresh_sessions 外键从 `user_id BIGINT` 改为 `subject`。
- JWT 主体从 `uid`(long) 改为 `sub`(subject);角色仍本地。
- 删除 `/auth/register`、`/auth/login`、`/auth/password/change`。
- 新增 OIDC RP:`/auth/oidc/enabled`、`/auth/oidc/start`、`/auth/oidc/callback`、
  `/auth/logout/sso`(PKCE S256 + state/nonce + JWKS 验签)。
- 登录页只保留「统一身份登录」;新增运维 break-glass(密封凭据/CLI,短时 ADMIN,
  审计,默认关闭),不是用户账号。
- 契约:`openapi.json` 更新;`contracts/` 增加虎符 RP 契约。

## Non-goals

- 不改钱包/账本记账规则、支付抽象、价目与拓扑管子的业务语义。
- 不做 MFA、社交登录聚合(由虎符承接)、多租户 IdP。
- 不保留本地密码作为常态化回落。

## Impact

- **破坏性 schema 变更**:零存量直接重建;如未来有数据,先显式绑定 sub 再切外键。
- 前端登录/回调/登出流程、API 契约、测试与门禁全部更新。
- 新登录依赖虎符;已签发 token/本地会话按 TTL 继续有效。

## Rollout

1. 设计/迁移与失败测试 → 2. principals/外键/JWT → 3. OIDC RP → 4. 前端与契约 →
5. `make check` + 联调;失败可回滚到上一版本(旧库快照)。
