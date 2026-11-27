# Proposal: 2026-11-26-add-service-identity-lifecycle（服务身份生命周期（service-identity-lifecycle））

## Why

控制面与 kongming/muniu 双向调用都需要服务身份；现状只写了“认证”，没有 Keycloak client/role 开通、短时凭据、轮换/吊销 owner，容易出现长期密钥、权限过大或无法吊销。

## What Changes

- 服务身份目录：service_id/方向/client/roles/scopes/environment。
- Keycloak client credentials + 短时 token；锦官→服务只读 scope；服务→控制面按注册/快照/命令/用量分权。
- 双密钥重叠轮换、吊销、审计；本地 pre-shared 仅 localhost。

## Non-goals

- 不做用户 SSO/Keycloak 替代。
- 不管理服务域凭据/key 池/OAuth 账号。
- 不做 managed 长期静态 token。
- 不共享用户会话。

## Capabilities

### New Capabilities

- `service-identity-lifecycle`：机器身份目录、凭据轮换与吊销

### Modified Capabilities

无

## Impact

Keycloak clients/roles、锦官身份目录/runbook、服务配置注入；不改用户身份或服务域凭据。
