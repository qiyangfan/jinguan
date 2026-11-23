# Runbook:单一身份与 break-glass

> 适用 2026-11-21 起的锦官:人类登录只经Keycloak OIDC;本地无密码、注册、改密。
> 回滚以旧库快照 + 旧版本镜像为准,V14 为破坏性迁移。

## 1. 配置

| 环境变量 | 说明 |
| --- | --- |
| `JINGUAN_OIDC_ENABLED` | 默认 false;true 时启用 RP |
| `JINGUAN_OIDC_ISSUER` | Keycloak issuer,如 `https://id.example.com` |
| `JINGUAN_OIDC_CLIENT_ID` / `_CLIENT_SECRET` | 在Keycloak登记 confidential client;secret 只从环境变量注入 |
| `JINGUAN_OIDC_REDIRECT_URI` | 必须与Keycloak登记完全一致:`<jinguan>/auth/oidc/callback` |
| `JINGUAN_OIDC_POST_LOGOUT_REDIRECT_URI` | 登出后回跳,须在Keycloak登记 |
| `JINGUAN_OIDC_FRONTEND_SUCCESS_URL` | 回调成功后的前端页,默认 `/` |
| `JINGUAN_BREAKGLASS_ENABLED` | 默认 false |
| `JINGUAN_BREAKGLASS_TOKEN` | 运维密封凭据;只在 break-glass 期间注入,用后轮换 |
| `JINGUAN_BREAKGLASS_TTL_MINUTES` | 默认 15;不得长期开启 |

Keycloak 侧准备:

1. 建 confidential client(如 `jinguan-web`),redirect 精确登记
   `https://jinguan.example/auth/oidc/callback`,启用 PKCE S256。
2. 在 client 上建 `jinguan-admin`/`jinguan-user` 角色并赋给用户;或接入上游身份源。
3. client secret 只注入 `JINGUAN_OIDC_CLIENT_SECRET`,不入库、不进日志。

## 2. 首登与授权

1. 用户经 Keycloak 登录;锦官只读 id_token 的
   `resource_access["jinguan-web"].roles`,白名单映射 `jinguan-admin → ADMIN`、
   `jinguan-user → USER`;未知角色与 realm roles 丢弃,缺失 claim 按 USER。
2. 首登按映射创建 `principals(subject, username, role)`;已存在主体在**重新登录**时
   同步角色缓存;refresh 沿用缓存,不回源 IdP。本地不再提供角色授予端点
   (`POST /admin/users/{subject}/role` 已移除)。
3. 角色授予/撤销在 Keycloak 侧完成(client roles):

```bash
# 以 Keycloak 管理员在 client jinguan-web 为用户赋/删 jinguan-admin
# 控制台:Users → 用户 → Role mapping → Assign role → jinguan-web
# 变更在用户下一次重新登录时收敛。
```

4. 钱包入账仍走 `/wallet/admin/credit`(body `subject`),账本 append-only。

## 3. Keycloak不可用

- 新登录失败;已签发 access/refresh 按 TTL 继续,本地验签不依赖Keycloak。
- 不要用 break-glass 替代日常登录;它只用于运维处置,默认关闭、逐次审计。
- 恢复登录页失败提示见前端;回滚以恢复旧库快照与旧版本为准。

## 4. break-glass

CLI(无 Web 模式,stdout 只输出 token):

```bash
export JINGUAN_BREAKGLASS_ENABLED=true
export JINGUAN_BREAKGLASS_TOKEN="$(openssl rand -base64 32)"
TOKEN=$(java -jar jinguan.jar break-glass)
```

HTTP 入口(同一服务、同一凭据):

```bash
# 开启(仅故障窗口)
export JINGUAN_BREAKGLASS_ENABLED=true
export JINGUAN_BREAKGLASS_TOKEN="$(openssl rand -base64 32)"
# 重启实例;凭据用后立即移除并轮换
```

审计表 `break_glass_audit` 记录 subject、来源 IP、签发了间与到期。运维结束必须关闭开关。

## 5. 回滚演练记录(2026-11-21)

1. 以 `spring.flyway.target=13` 建旧 schema 快照(V13,`users` 存在、`principals` 不存在)。
2. `pg_dump -Fc` 备份后恢复:版本回到 13,表状态一致。
3. 正常启动应用:迁移推进到 V16,`users` 删除、`principals/oidc_login_states/break_glass_audit` 就绪。
4. 结论:旧库快照可恢复;生产回滚 = 停新版 → 恢复快照 → 起旧版本镜像。

## 6. 健壮性

- OIDC 发现文档连接 3s/读取 5s;token 交换同样 3s/5s,上游挂起会按读超时失败,不占满请求线程。
- 并发首登同一 subject:唯一键冲突后重读已建 principal,回调幂等成功,不会 500/400。
- SSO 登出:Keycloak `/end_session` 支持 RP 只带 `client_id` + 登记回跳(无 `id_token_hint`),撤销会话后 302;锦官旧 refresh 401。

## 7. 真实联调记录(2026-11-21,历史)

> 当时的 IdP 为虎符(已退役);当前 Keycloak 联调见 §8,以下原文保留。

- 虎符 `http://127.0.0.1:8080` + 锦官 `http://127.0.0.1:8090`,client `jinguan-interop`。
- 授权码 + PKCE + state cookie:回调 302 到 `/wallet`,`/auth/refresh` 200。
- 首登:`principals` 为 USER,`/wallet/balance` 200 且余额 0,`/admin/users/*/role` 403。
- 本地禁用:refresh 403;恢复后正常。
- 虎符禁用账号:新登录 401「用户名或密码错误」。
- `/auth/logout/sso` 302 至虎符 `end_session`,浏览器跟随后回到 `/login` 200;虎符会话撤销,旧 refresh 401。

## 8. Keycloak client roles 联调记录(2026-11-23)

环境:Keycloak 26.8.2(`http://127.0.0.1:8080`,realm `longzhong`)+ 锦官 `:8090`,
client `jinguan-web`;测试用户 `demo`(jinguan-admin)、`demo-user`(jinguan-user)。

- ID token 默认不含 `resource_access`;已在 platform `setup-realm.sh` 为 `jinguan-web`
  增加仅限本 client 的 ID token 角色 mapper(`id.token.claim=true`),幂等重跑通过。
- 登录/refresh:demo → `role=ADMIN`;demo-user → `role=USER`;refresh 沿用缓存角色。
- `USER` 调 `/admin/users/*/disable` 403;`ADMIN` 禁用/启用 204。
- 撤销 `jinguan-admin` 后重新登录降为 USER、管理端点 403;重新授予后重登恢复 ADMIN。
- break-glass 签发短时 ADMIN,管理端点 204。
- `/auth/logout/sso` 302 至 Keycloak `end_session`;本地 refresh 立即 401。
- 账本:`/wallet/balance` 按 Keycloak `sub` 归属,200。
- JWKS:登录链路实际使用 Keycloak JWKS 验签;锦官 `/.well-known/jwks.json` 正常发布。

## 9. 生产签名密钥(上线阻断项)

- 生产 MUST 挂载 `JINGUAN_JWT_KEYS_DIR`:`active.pem`(PKCS#8,0600,运行用户可读)+
  `retired/*.pem`(只读)。缺失/不可读即视为部署失败;禁止生产依赖进程内临时密钥
  (重启即全员掉线)。
- 轮换:生成新钥 → 替换 `active.pem` → 旧钥移入 `retired/` → 重启 → 旧 refresh 过期
  (默认 7 天)后删除退役公钥。JWKS 全程同时发布新旧公钥。
- 生产部署必须设置 `JINGUAN_REQUIRE_PERSISTENT_KEYS=true`:缺密钥目录即启动失败,
  不再退化为临时密钥;部署脚本仍应前置检查目录与权限。

## 10. IdP 停机演练(隔离环境;禁止停共享 8080)

- 前置:第二套 Keycloak(独立容器/卷,8081)或 OIDC stub;jinguan 实例 8091 指向它。
  不得触碰 muniu/jinguan 共用的 8080 实例。
- 步骤:
  1. 在 8091 完成登录,取得 access 与 refresh cookie。
  2. 停止 8081。
  3. 验证:已签发 access 在 TTL 内可访问;`POST /auth/refresh` 200(本地缓存角色,不回源);
     新 `/auth/oidc/start` 在 discovery 缓存(10 分钟)内仍 302、冷缓存 502;登录页提示可读、
     无 500。
  4. 登出:本地 refresh 立即 401;`end_session` 不可达时回落登录页。
  5. 重启 8081:新登录 200。
- 停止条件:误触共享 8080/共享 DB 立即中止;生产演练需维护窗口与回滚脚本。
- 2026-11-23 已按隔离实例演练通过:停机前登录取 token;停机后已签发 access 200、
  refresh 200(本地缓存)、新 `/auth/oidc/start` 302 指旧 IdP(缓存期内)、本地登出后
  旧 refresh 401;重启 IdP 后新登录 200。共享 8080 未触碰。
