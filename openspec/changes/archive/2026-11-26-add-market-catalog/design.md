# Design: 商店目录与权益（market-catalog）

## Context

现有 bot-store 目录从租户内上架快照派生；没有跨租户目录/权益。

## Goals / Non-Goals

**Goals:**
- 目录/权益单一来源。
- 定义与运行解耦。

**Non-Goals:**
- 不做 Bot 运行。
- 不做真实交易。

## Decisions

1. 目录条目只存引用（bot_key/tenant）与运营字段，不复制定义。
2. 可见性/权益按租户/团队；目录快照版本化。
3. 运营标记只影响目录展示，不改变运行权限。

## Risks / Trade-offs

- 引用失效 → 目录条目显示不可用，不静默隐藏。
- 权益误配 → 审计 + 回滚。

## Migration Plan

greenfield 新增表。
