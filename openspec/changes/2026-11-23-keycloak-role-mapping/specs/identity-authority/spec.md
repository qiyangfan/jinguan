# 身份权威:粗角色映射

## MODIFIED Requirements

### Requirement: 授权留本地

角色必须 (MUST) 由 Keycloak client role 白名单映射提供(见 auth-session「授权留本地」),
本地只缓存并在登录时同步;锦官不得 (MUST NOT) 提供角色授予入口。租户、钱包、停服与
break-glass 仍由锦官本地管理,不得 (MUST NOT) 从 token 接收租户/钱包/权限点。

#### Scenario: 首登无权限

- **GIVEN** id_token 不含 `jinguan-admin`
- **WHEN** 新 subject 首次登录
- **THEN** 建立 `USER` 资料且无钱包/管理权限

#### Scenario: IdP 角色为准

- **WHEN** Keycloak 侧角色变更
- **THEN** 该主体下次重新登录后收敛为最新映射;锦官不再提供本地角色授予端点
