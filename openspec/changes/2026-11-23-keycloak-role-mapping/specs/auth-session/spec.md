# 粗角色映射(Keycloak client roles)

## MODIFIED Requirements

### Requirement: 授权留本地

首登必须 (MUST) 按 Keycloak client role 白名单映射建立本地角色:
`jinguan-admin → ADMIN`、`jinguan-user → USER`,白名单外角色丢弃,无命中按 `USER`;
角色以 IdP 为准,本地 `principals.role` 仅作缓存并在每次登录时同步。租户、钱包、账本归属、
停服与 break-glass 必须 (MUST) 留本地,不得 (MUST NOT) 从 token 接收。

#### Scenario: 首登无权限

- **GIVEN** id_token 不含 `jinguan-admin`
- **WHEN** 新 subject 首次登录
- **THEN** 建立 `USER` 资料且无钱包/管理权限

#### Scenario: 首登角色映射

- **GIVEN** id_token 的 `resource_access["jinguan-web"].roles` 含 `jinguan-admin`
- **WHEN** 该 subject 首次登录
- **THEN** 本地主体角色为 `ADMIN`

#### Scenario: 未知角色丢弃

- **WHEN** token 含 `jinguan-superuser` 等白名单外角色
- **THEN** 忽略该角色;无白名单命中时按 `USER` 建号,登录不报错

#### Scenario: 角色以 IdP 为准收敛

- **GIVEN** 某主体本地缓存为 `ADMIN`
- **WHEN** Keycloak 撤销其 `jinguan-admin` 后重新登录
- **THEN** 本地缓存与自签 token 角色收敛为 `USER`

#### Scenario: 租户与钱包不由 token 决定

- **WHEN** id_token 携带任何租户或钱包类 claim
- **THEN** 忽略;归属与余额以本地数据为准
