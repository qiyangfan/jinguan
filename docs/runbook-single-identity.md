# Runbook:单一身份与 break-glass

> 适用 2026-11-21 起的锦官:人类登录只经虎符 OIDC;本地无密码、注册、改密。
> 回滚以旧库快照 + 旧版本镜像为准,V14 为破坏性迁移。

## 1. 配置

| 环境变量 | 说明 |
| --- | --- |
| `JINGUAN_OIDC_ENABLED` | 默认 false;true 时启用 RP |
| `JINGUAN_OIDC_ISSUER` | 虎符 issuer,如 `https://id.example.com` |
| `JINGUAN_OIDC_CLIENT_ID` / `_CLIENT_SECRET` | 在虎符登记 confidential client;secret 只从环境变量注入 |
| `JINGUAN_OIDC_REDIRECT_URI` | 必须与虎符登记完全一致:`<jinguan>/auth/oidc/callback` |
| `JINGUAN_OIDC_POST_LOGOUT_REDIRECT_URI` | 登出后回跳,须在虎符登记 |
| `JINGUAN_OIDC_FRONTEND_SUCCESS_URL` | 回调成功后的前端页,默认 `/` |
| `JINGUAN_BREAKGLASS_ENABLED` | 默认 false |
| `JINGUAN_BREAKGLASS_TOKEN` | 运维密封凭据;只在 break-glass 期间注入,用后轮换 |
| `JINGUAN_BREAKGLASS_TTL_MINUTES` | 默认 15;不得长期开启 |

虎符侧准备:

```bash
hufu admin client create --client-id jinguan-prod --type confidential \
  --redirect-uri https://jinguan.example/auth/oidc/callback \
  -scope openid,profile,email,offline_access
hufu admin user create --username alice   # 或由虎符上游联合登录
```

## 2. 首登与授权

1. 用户经虎符登录;锦官只创建 `principals(subject, username, role=USER)`。
2. 首登无钱包、无 ADMIN;管理员用 break-glass 或既有 ADMIN 授予角色:

```bash
# 以 break-glass 取得短时 ADMIN token
TOKEN=$(curl -sS -X POST https://jinguan.example/auth/break-glass \
  -H "X-Break-Glass-Token: $JINGUAN_BREAKGLASS_TOKEN" | jq -r .token)
curl -sS -X POST https://jinguan.example/admin/users/<subject>/role \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"role":"ADMIN"}'
```

3. 钱包入账仍走 `/wallet/admin/credit`(body `subject`),账本 append-only。

## 3. 虎符不可用

- 新登录失败;已签发 access/refresh 按 TTL 继续,本地验签不依赖虎符。
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
- SSO 登出:虎符 `/end_session` 支持 RP 只带 `client_id` + 登记回跳(无 `id_token_hint`),撤销会话后 302;锦官旧 refresh 401。

## 7. 真实联调记录(2026-11-21)

- 虎符 `http://127.0.0.1:8080` + 锦官 `http://127.0.0.1:8090`,client `jinguan-interop`。
- 授权码 + PKCE + state cookie:回调 302 到 `/wallet`,`/auth/refresh` 200。
- 首登:`principals` 为 USER,`/wallet/balance` 200 且余额 0,`/admin/users/*/role` 403。
- 本地禁用:refresh 403;恢复后正常。
- 虎符禁用账号:新登录 401「用户名或密码错误」。
- `/auth/logout/sso` 302 至虎符 `end_session`,浏览器跟随后回到 `/login` 200;虎符会话撤销,旧 refresh 401。
