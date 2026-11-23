# 设计:Keycloak client roles 白名单映射

## 角色来源与映射

| Keycloak 事实 | 锦官本地 | 说明 |
| --- | --- | --- |
| client `jinguan-web` 的 role `jinguan-admin` | `ADMIN` | 最高优先,存在即 ADMIN |
| client `jinguan-web` 的 role `jinguan-user` | `USER` | 默认角色 |
| 其他任何角色 / 缺失 claim | `USER` | 丢弃未知角色,不报错 |
| realm roles / 其他 client roles | 忽略 | 防止跨产品角色泄漏 |

- claim 路径:`resource_access["jinguan-web"].roles`(client roles);不消费
  `realm_access`。
- 判定顺序:ADMIN 优先;同 token 同时含两者取 ADMIN。
- 角色名白名单硬编码在 RP 内;新增角色必须改代码 + 规格,不接受任意透传。

## 同步与生效

- 登录回调:验签通过后解析角色 → 首登按映射创建 `principals.role`,已存在则更新缓存。
- refresh:续期沿用已缓存角色,不做 IdP 回源;角色撤销在下一次**重新登录**时收敛。
  如需更快的撤销窗口,可选缩短 access TTL 或引入背通道登出,不在本 change 范围。
- 本地 `principals.role` 仅作缓存与审计视图;不再是授权事实源。

## 安全边界

- 租户、钱包、账本归属、停服、break-glass 全部留本地;token 中的同名 claim 一律忽略。
- ADMIN 端点三层强制不变(URL 层 + `@PreAuthorize` + 前端体验层)。
- 角色名不来自用户输入,无法注入;未知角色静默丢弃并记审计(可选)。

## 失败模式

| 场景 | 行为 |
| --- | --- |
| IdP 不可达 | 新登录失败;已签发 token 本地验签继续;refresh 用本地缓存角色续签 |
| token 缺 `resource_access` | 按 USER;不阻断登录 |
| Keycloak 撤销 ADMIN | 该用户下次重登/续期后降为 USER;旧 access 至多 TTL 内仍为 ADMIN |
| 角色名拼写错误 | 视为未知角色丢弃,不会授予 ADMIN |

## 实施前事实核查(2026-11-23,Keycloak 26.8.2)

- 默认配置下 **id_token 不携带 `resource_access`**:内置 `roles` client scope 的
  `client roles` mapper 只设 `access.token.claim=true`,`id.token.claim` 缺省 false;
  `generate-example-id-token` 实测 claims 无 `resource_access`/`realm_access`。
- 采用方案:在 client `jinguan-web` 增加 `oidc-usermodel-client-role-mapper`
  (`claim.name=resource_access.jinguan-web.roles`、`id.token.claim=true`、
  `access.token.claim=false`),只映射本 client 角色,不把其它产品角色带进 ID token;
  幂等逻辑在 platform `setup-realm.sh`。
- 实测结果:demo(jinguan-admin)的 example id_token 含
  `resource_access.jinguan-web.roles=["jinguan-admin"]`,`realm_access` 为空。
- 结论:RP 继续从 **id_token** 读取角色,不需要解析 access_token。

## 测试矩阵

1. `jinguan-admin` → ADMIN;`jinguan-user` → USER;两者并存 → ADMIN。
2. 未知角色 → USER;缺 claim → USER;realm role → 忽略。
3. 首登建档角色正确;既有主体角色被 IdP 变更后,重登同步。
4. token 携带 tenant/wallet 类 claim → 全部忽略。
5. 角色退役端点:旧 `POST /admin/users/{subject}/role` 返回 404/405。
6. 现有安全用例回归:USER 访问管理端点 403、ADMIN 200、break-glass/禁用语义不变。