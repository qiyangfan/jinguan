# Proposal: 2026-11-26-add-policy-distribution（策略定义与下发（policy-distribution））

## Why

跨服务共享策略包的激活、作用域与版本应由控制面拥有；服务域规则体、规则类型与执行仍归各服务。

## What Changes

- 新增共享策略包引用、作用域、激活状态与版本。
- 输出策略包快照，服务本地执行。
- 支持激活/回滚与审计。
- 不存规则体；服务 admin API 维护规则类型/规则体，控制面只激活共享包。

## Non-goals

- 不做服务端策略执行实现。
- 不做任意脚本下发/通用策略 DSL。
- 不拥有服务域护栏规则定义。
- 不做跨租户策略继承的复杂模型。

## Capabilities

### New Capabilities

- `policy-distribution`：护栏/访问策略定义、版本与下发

### Modified Capabilities

无

## Impact

新增控制面策略表/API/快照；服务执行侧另立 change。
