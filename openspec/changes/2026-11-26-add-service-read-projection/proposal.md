# Proposal: 2026-11-26-add-service-read-projection（服务数据只读投影（service-read-projection））

## Why

管理域必须只读服务数据；当前没有服务只读投影契约，服务宕机时只能直连库或显示空白，破坏独立部署与 schema 私有。

## What Changes

- 定义服务只读投影 envelope：source/observed_at/stale/ETag/字段白名单。
- 锦官经服务只读 API/投影拉取并缓存 last-known；服务宕机显示 stale，无投影显示未知。
- 服务身份/管理员只读认证；敏感字段不返回、不缓存。
- 服务只读 API base URL 由锦官部署配置按 service_id 静态映射；注册表不发现 endpoint。
- 明确只读 = API/投影，不是直连库/跨库 JOIN/CDC。

## Non-goals

- 不直连服务 DB、跨库 JOIN 或 CDC。
- 不代理写、不反向同步、不产生第二写者。
- 不缓存凭据/token/密码/密钥/连接串。
- 不替代服务管理台的配置写。

## Capabilities

### New Capabilities

- `service-read-projection`：服务数据只读投影、缓存与 stale 语义

### Modified Capabilities

无

## Impact

jinguan 只读投影模型/缓存/端点；服务只读 API 客户端与认证；不写服务库、不修改服务运行时。
