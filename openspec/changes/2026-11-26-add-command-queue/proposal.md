# Proposal: 2026-11-26-add-command-queue（运行时命令队列（command-queue））

## Why

控制台需要在服务恢复后执行跨服务 reindex/清缓存/取消任务等运行时命令；当前没有统一命令通道。服务本地运行命令不进入控制面队列。

## What Changes

- 定义跨服务命令 envelope、状态与幂等键。
- 命令带 action/params schema/version；参数按 X-01 action schema 校验。
- 服务身份认证；服务 claim/执行/ack，控制台显示 pending/failed。
- 命令审计与结果回执。
- 本地运行命令由服务管理台直接执行，控制面缺席仍可用。

## Non-goals

- 不做任意 SQL/脚本下发。
- 不做共享控制状态变更（走快照）。
- 不做实时双向通道。
- 不接管服务本地运行命令。

## Capabilities

### New Capabilities

- `command-queue`：运行时命令 envelope、claim/ack 与回执

### Modified Capabilities

无

## Impact

新增控制面命令表/API；服务执行器另立 change。
