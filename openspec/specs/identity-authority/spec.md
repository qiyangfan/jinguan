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

首登只创建无权限资料;角色/钱包/停服仍由锦官管理员显式授予;不得 (MUST NOT)
从 token 接收产品角色/租户。

#### Scenario: 首登无权限

- **WHEN** 新 subject 首次登录
- **THEN** 建立 USER 资料且无钱包/权限

### Requirement: 可用性与 break-glass

Keycloak不可用时新登录失败;已签发 token/本地会话按 TTL 继续;break-glass 必须 (MUST)
仅限运维、审计、默认关闭、不得作为普通账号。

#### Scenario: IdP 故障

- **WHEN** Keycloak不可达
- **THEN** 新登录失败,既有 access token 仍可本地验签
