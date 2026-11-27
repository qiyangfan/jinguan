# Proposal: 2026-11-26-add-control-snapshot（控制快照契约（control-snapshot））

## Why

服务需要单一、版本化、可缓存、可认证的共享控制快照；当前各域各自下发，缺统一契约，且控制端点匿名。

## What Changes

- 定义快照 envelope：版本/ETag/observed_at/投影。
- 按服务投影租户/权益/策略包/目录/价目/拓扑。
- last-known/失效/fail-closed 语义与安全类清单。
- TLS + 锦官 RSA/JWKS 签名 + 服务身份认证；拒绝匿名/未签名快照。

## Non-goals

- 不做命令下发（命令队列另域）。
- 不做服务端执行实现。
- 不做增量 delta 的复杂协议。
- 不做服务域配置下发；服务域配置走服务 admin API。

## Capabilities

### New Capabilities

- `control-snapshot`：版本化控制快照与按服务投影

### Modified Capabilities

无

## Impact

新增控制面快照组装/端点/签名校验契约；服务消费另立 change；现有匿名 `/control/v1/*` 须收口。
