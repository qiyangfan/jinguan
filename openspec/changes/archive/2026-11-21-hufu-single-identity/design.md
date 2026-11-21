# 设计:锦官去账号化 + 虎符 OIDC RP

## 数据模型(零存量重建)

```sql
-- 主体:subject 为虎符 sub(UUID 文本);角色/停服仍本地
CREATE TABLE principals (
    subject      VARCHAR(64) PRIMARY KEY,
    username     VARCHAR(64) NOT NULL UNIQUE,
    role         VARCHAR(16) NOT NULL DEFAULT 'USER',
    disabled     BOOLEAN NOT NULL DEFAULT FALSE,
    suspended_at TIMESTAMP NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 外键全部改 subject;其余列与 append-only 语义不变
ALTER TABLE ledger_entries       ADD COLUMN subject VARCHAR(64) REFERENCES principals(subject);
ALTER TABLE mock_payment_orders  ADD COLUMN subject VARCHAR(64) REFERENCES principals(subject);
ALTER TABLE api_key_bindings     ADD COLUMN subject VARCHAR(64) REFERENCES principals(subject);
ALTER TABLE refresh_sessions     ADD COLUMN subject VARCHAR(64) REFERENCES principals(subject);
-- 零存量:直接新表/新列并删除旧 user_id/旧 users;有存量则先绑定再切。
```

## JWT 与会话

- `JwtService` 的 `uid` claim 改为 `sub`(subject);`LoginUser` 以 `subject` 为主键。
- `JwtAuthFilter` 以 subject 绑定认证;钱包/支付/API key 查询按 subject。
- refresh 会话表按 subject;轮换/重用检测逻辑不变。

## OIDC RP

- 配置:`jinguan.oidc.{enabled,issuer,client-id,client-secret,redirect-uri,
  post-logout-redirect-uri,frontend-success-url}`;secret 只走环境变量。
- 状态:一次性 `state` + PKCE verifier + nonce 落库(短 TTL、原子消费);回调校验
  `iss/aud/exp/nonce` 与 JWKS 签名。
- 回调:验签 → 按 `sub` 查 principals → 无则建无权限资料(USER,无钱包) →
  签发本地 access/refresh(sub=subject)、写 refresh 会话、下发 HttpOnly cookie →
  302 到前端成功页;前端调 `/auth/refresh` 取 access,不把 token 放 URL。
- 授权:角色/停服/钱包仍由锦官管理员本地授予;首登无权限。
- 登出:`/auth/logout/sso` 吊销本地 refresh 后 302 至虎符 `end_session`(client_id +
  登记的回跳);本地 `/auth/logout` 保持原语义。
- 禁用:虎符拒登/吊销 refresh;锦官按 principals.disabled 每请求拦截;收敛窗口=access TTL。

## JIT 与授权

- 首登只建 `principals`(USER、无钱包、无权限);管理员显式授予角色/钱包并审计。
- 禁止按 email/username 自动合并;不接收产品授权 claim。

## break-glass

- 独立密封凭据/CLI,可铸造短时 ADMIN 会话(不入 principals);默认关闭、审计、
  可轮换;仅用于虎符故障时的运维处置。

## 兼容与回滚

- 破坏性 schema;发布前备份/快照。回滚=恢复旧版本与旧库快照。
- 旧 JWT(uid)在切换后不再被接受;access TTL 内完成切换,必要时提前缩短 TTL。

## 测试

- 先红:本地登录/注册接口删除、subject 外键、state 单次/nonce/PKCE、无权限首登、
  break-glass、离线验签与 JWKS 缓存。
- 迁移在 H2/PostgreSQL 双模式验证;`make check` 全绿。
