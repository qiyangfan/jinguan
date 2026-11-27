# Proposal: 2026-11-26-add-market-catalog（商店目录与权益（market-catalog））

## Why

商店目录/权益应由控制面拥有；当前 tenant 内商店目录与权益散在 kongming，无法统一运营。

## What Changes

- 新增目录条目，引用 kongming Bot 标识。
- 定义可见性/权益/运营标记。
- 输出目录快照，kongming 货架消费。

## Non-goals

- 不拥有 Bot 定义/版本/运行。
- 不做支付/结算。
- 不做跨租户推荐算法。

## Capabilities

### New Capabilities

- `market-catalog`：商店目录、可见性/权益与运营标记

### Modified Capabilities

无

## Impact

新增控制面目录表/API/快照；kongming 货架消费另立 change。
