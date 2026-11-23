# identity-authority Specification

## Purpose
锦官不再拥有可登录的本地账号与密码体;所有人类身份统一来自Keycloak OIDC 的 `sub`,本地只保存按 subject 归属的授权数据、账本与资源。break-glass 仅作运维应急通道,不构成第二套账号体系。

## Requirements

### Requirement: 无本地账号

锦官不得 (MUST NOT) 提供注册、密码登录、改密或本地管理员账密;所有人类登录必须
(MUST) 经Keycloak OIDC;用户主键必须 (MUST) 为Keycloak `sub`。

#### Scenario: 本地登录拒绝

- **WHEN** 调用旧 `/auth/login`
- **THEN** 不存在该端点

#### Scenario: subject 外键

- **WHEN** 账本/订单/API key/refresh 写入
- **THEN** 归属列为Keycloak subject

### Requirement: OIDC RP 安全

授权必须 (MUST) 使用授权码 + PKCE(S256);回调必须 (MUST) 校验 state 单次、
nonce、iss/aud/exp 与 JWKS 签名;错误不得回显 token/code/secret。

#### Scenario: state 重放

- **GIVEN** 已完成一次回调
- **WHEN** 重复使用 state
- **THEN** 拒绝

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

### Requirement: 可用性与 break-glass

Keycloak不可用时新登录失败;已签发 token/本地会话按 TTL 继续;break-glass 必须 (MUST)
仅限运维、审计、默认关闭、不得作为普通账号。

#### Scenario: IdP 故障

- **WHEN** Keycloak不可达
- **THEN** 新登录失败,既有 access token 仍可本地验签
