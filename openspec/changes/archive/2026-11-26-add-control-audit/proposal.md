# Proposal: 2026-11-26-add-control-audit（控制面管理审计（control-audit））

## Why

控制面将拥有租户/套餐/策略/目录/命令写权限；必须有统一不可变审计。

## What Changes

- 新增控制面审计表/API。
- 覆盖所有控制数据/命令写操作。
- 查询/导出/保留；脱敏。

## Non-goals

- 不做外部 SIEM。
- 不审计运行时数据内容。
- 不存凭据。

## Capabilities

### New Capabilities

- `control-audit`：控制面管理审计记录、查询与导出

### Modified Capabilities

无

## Impact

新增控制面审计表/API；控制台审计页；不修改服务运行时库。
