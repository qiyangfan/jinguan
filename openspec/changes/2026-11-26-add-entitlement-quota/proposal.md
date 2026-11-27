# Proposal: 2026-11-26-add-entitlement-quota（套餐、配额与权益（entitlement-quota））

## Why

控制面必须拥有跨服务共享的套餐/配额/权益；当前这些散落在各服务或缺失，服务无法统一执行。服务域配置/权限码不上收。

## What Changes

- 新增套餐、配额、权益与功能开关目录；能力/权益键来自服务注册。
- 租户绑定与版本化快照；服务按快照执行。
- 控制面聚合用量；服务域配置不进入锦官。

## Non-goals

- 不做支付/发票（钱包/支付已有域）。
- 不做服务端配额计数实现。
- 不定义服务内部权限码/工具配置。
- 不做跨租户共享配额池。

## Capabilities

### New Capabilities

- `entitlement-quota`：套餐/配额/权益目录、绑定与快照

### Modified Capabilities

无

## Impact

新增控制面表/API/快照；服务执行侧另立 change；不直接修改服务库。
