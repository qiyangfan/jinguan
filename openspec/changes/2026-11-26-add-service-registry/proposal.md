# Proposal: 2026-11-26-add-service-registry（服务注册与健康（service-registry））

## Why

控制面需要知道服务实例/能力/健康，才能下发快照、排队命令、只读展示服务域配置并在控制台展示状态。

## What Changes

- 新增服务实例注册、能力/权益键、版本与服务 admin API 能力。
- 服务身份认证；心跳/健康与 last_seen。
- 控制台只读视图；不存服务凭据。
- 能力目录供策略包/权益引用，不复制服务域配置。
- 注册 payload 不含服务只读 API endpoint；服务地址由锦官部署配置按 service_id 静态映射。

## Non-goals

- 不做服务发现/负载均衡。
- 不做 endpoint 发现/路由；服务只读 API 地址由锦官静态配置。
- 不做服务域配置下发（快照另域；配置走服务 admin API）。
- 不做跨集群拓扑管理。
- 不做匿名注册/心跳。

## Capabilities

### New Capabilities

- `service-registry`：服务实例、能力、版本与健康

### Modified Capabilities

无

## Impact

新增控制面注册表/API；服务侧注册客户端见 kongming/muniu 的 `service-registration-client` change；控制台只读。
