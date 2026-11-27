# Proposal: 2026-11-26-add-control-console（锦官控制台（control-console））

## Why

控制面需要自己的 admin UI，拥有共享控制数据真实读写权，并提供服务域配置只读展示；当前 jinguan web 只有钱包/价目/拓扑/绑定。

## What Changes

- 新增租户/成员/套餐/权益/策略包/目录/服务/命令/审计页面，并保留钱包/支付/价目/拓扑/绑定页。
- 服务域配置页经服务只读 API 展示，标注“只读”；不直连服务 DB、不复制业务逻辑。
- 共享控制读写与安全确认；服务不可用时共享控制页可用、服务域配置页显示不可用/stale。
- SSO/登录仍跳符节/Keycloak；锦官只是 RP。

## Non-goals

- 不直连服务运行时库/服务域配置库。
- 不把服务域配置/凭据/运行时数据复制进锦官。
- 不做通用 SQL 控制台。
- 不替代 Keycloak 管理台/SSO。

## Capabilities

### New Capabilities

- `control-console`：锦官控制台 UI、共享控制读写与服务域配置只读展示

### Modified Capabilities

无

## Impact

jinguan/web 路由/组件；控制面 API；服务只读 API 客户端/只读视图契约；不修改服务运行时。
