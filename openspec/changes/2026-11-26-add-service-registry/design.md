# Design: 服务注册与健康（service-registry）

## Context

现有服务没有统一注册/健康上报；控制台无法判断服务可用性，也无法获知服务 admin API 能力。

## Goals / Non-Goals

**Goals:**
- 服务状态与 admin API 能力可发现。
- 快照/命令/只读视图可按服务投影。

**Non-Goals:**
- 不做请求路由。
- 不做服务实例调度。
- 不做服务域配置复制。

## Decisions

1. 服务主动注册/心跳；控制面只存状态，不主动探测。
2. 能力/权益键/版本/admin API 能力为显式字段，供快照/策略包/只读视图引用。
3. 注册/心跳必须服务身份；匿名拒绝。
4. 超时即 stale，控制台显示，不自动改服务状态。
5. 不存凭据/服务域配置。
6. 注册 payload 不含 endpoint；服务只读 API base URL 由锦官部署配置按 service_id 静态映射；注册表不做服务发现。
7. stale 记录按保留期清理/归档并审计；活跃实例/审计不删；不伪造健康。

## Risks / Trade-offs

- 心跳丢失误判 → stale 而非立即失败；控制台提示。
- 能力声明漂移 → 版本/契约测试/服务注册校验。

## Migration Plan

greenfield 新增表；服务侧注册客户端见 `2026-11-26-add-kongming-service-registration-client` 与 `2026-11-26-add-muniu-service-registration-client`。
