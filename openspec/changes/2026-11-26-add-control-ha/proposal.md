# Proposal: 2026-11-26-add-control-ha（控制面 HA 与恢复（control-ha））

## Why

控制面成为共享控制与命令的单一来源，必须可备份/可恢复；但服务已 last-known，v1 不把多实例 HA 作为前置。

## What Changes

- v1 基线：单实例 + 共享 PG + 备份/恢复/演练。
- 快照/命令/审计持久化；调度器单实例/租约。
- 多实例无状态 API + 故障切换作为后续触发项，不在本 change 强制。

## Non-goals

- v1 不做多实例控制面 HA/跨地域多活。
- 不做服务数据高可用。
- 不替代数据库自身 HA。
- 不以多实例 HA 阻塞服务迁移。

## Capabilities

### New Capabilities

- `control-ha`：控制面多实例、备份恢复与持久性

### Modified Capabilities

无

## Impact

部署/配置/备份脚本；控制面 API 无状态化；不修改服务运行时。
