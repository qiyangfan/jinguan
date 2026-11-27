# Design: 跨服务运行时命令队列（command-queue）

## Context

现有控制台无法在服务宕机时排队跨服务命令；服务也没有统一命令消费协议。服务本地运行命令已有本地能力，不应被控制面队列绑架。

## Goals / Non-Goals

**Goals:**
- 跨服务命令可靠、幂等、可追踪。
- 服务恢复后收敛。

**Non-Goals:**
- 不做任意脚本/SQL。
- 不接管本地运行命令。
- 不做第二写者。

## Decisions

1. 命令为有限动作枚举（reindex/cache_purge/task_cancel）；每个 action 带 params schema/version，参数按 X-01 校验；未知 action/版本拒绝；不接受任意 SQL。
2. 命令带幂等键/目标/原因/actor；服务以服务身份 claim 后执行并 ack。
3. pending/failed 可见；超时可重试，幂等键防重复。
4. 本地运行命令由服务管理台直接执行，控制面宕机不影响。
5. 命令状态/结果/dead-letter 按保留期清理；未 ack 不删；幂等键覆盖重试窗口；清理审计。

## Risks / Trade-offs

- 命令重复执行 → 幂等键 + 服务端执行幂等。
- 长命令超时 → 状态/心跳/重试上限。
- 控制/本地双通道 → 命令来源标注 + 统一审计，不改变数据写者。

## Migration Plan

greenfield 新增表。
