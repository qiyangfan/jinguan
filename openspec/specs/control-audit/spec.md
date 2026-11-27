# control-audit Specification

## Purpose

控制面管理审计：所有期望状态与命令写操作的不可变记录、查询与导出，为租户、套餐、策略、目录、命令提供统一追责事实源。

## Requirements

### Requirement: 控制操作审计

所有控制面写操作 SHALL 记录不可变审计：actor、角色、动作、目标、原因、脱敏摘要、结果、request_id 与时间；普通列表只读 SHALL NOT 写审计；敏感服务配置详情查看/导出 SHALL 记录只读审计；敏感值 SHALL NOT 入库。

#### Scenario: 租户变更留痕

- WHEN ADMIN 停用租户
- THEN 审计有 tenant、原因、actor、结果

#### Scenario: 命令留痕

- WHEN ADMIN 提交 reindex
- THEN 审计有命令、目标、结果

#### Scenario: 敏感只读审计

- WHEN ADMIN 查看敏感服务配置详情或导出
- THEN 审计有 actor/tenant/service/object/action/result/request_id，无字段值

#### Scenario: 普通列表不记

- WHEN ADMIN 浏览普通列表或健康摘要
- THEN 不写审计，避免噪音

### Requirement: 审计查询与导出

ADMIN SHALL 能按 actor/动作/目标/结果/时间查询并分页；导出 SHALL 脱敏且限权；在线保留期 SHALL 不少于 180 天；在线记录 SHALL 禁止更新/删除；到期归档/清理见下条。

#### Scenario: 筛选查询

- WHEN 按 actor 查询
- THEN 返回匹配记录

#### Scenario: 不可变

- WHEN 尝试在线删除审计
- THEN 拒绝

### Requirement: 归档与清理

审计 SHALL 在线保留不少于 180 天；到期 SHALL 归档/冷存；删除 SHALL 仅在保留期后由独立清理 job 执行并审计；清理 SHALL 可配置、可观测；SHALL NOT 影响在线查询或告警事件源。

#### Scenario: 到期归档

- WHEN 审计超过在线保留期
- THEN 归档到冷存，在线查询按策略可用

#### Scenario: 清理审计

- WHEN 独立 job 清理过期归档
- THEN 记录数量/范围/结果，可追责

#### Scenario: 不破坏事件源

- WHEN 告警规则读取审计事件
- THEN 归档/清理不产生假告警或漏告警
