# Design: 共享控制快照契约（control-snapshot）

## Context

现有 muniu 价目/拓扑/吊销各自拉取；kongming 的共享控制数据在本地；没有统一快照，且 `/control/v1/*` 匿名。

## Goals / Non-Goals

**Goals:**
- 一个版本化、可认证、可验签的共享控制快照契约。
- 服务可 last-known 启动。

**Non-Goals:**
- 不做实时推送。
- 不做跨服务共享 DB。
- 不下发服务域配置。

## Decisions

1. 快照为整包 + 版本/ETag；服务按需拉取。
2. 按服务能力投影，避免暴露无关数据。
3. 安全类（租户/RBAC/共享策略/吊销/权益）缺失 fail-closed；非安全类 last-known。
4. TLS + 锦官 RSA/JWKS 签名；服务验签；控制面接口不匿名。
5. 服务身份用 Keycloak client credentials；本地开发用 pre-shared，仅限 localhost；开通/轮换/吊销见 `2026-11-26-add-service-identity-lifecycle`。

## Risks / Trade-offs

- 快照过大 → 按服务投影/压缩。
- 版本回退 → 版本单调，拒绝旧版本覆盖。
- 签名密钥轮换 → JWKS 同时发布新旧公钥；服务缓存。

## Migration Plan

greenfield 新增端点；现有匿名控制端点收口；服务侧后续接入。
