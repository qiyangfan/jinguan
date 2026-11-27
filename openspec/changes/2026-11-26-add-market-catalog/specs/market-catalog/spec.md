# Spec Delta

## Purpose

控制面的商店目录：目录条目只引用运行时 Bot 标识，定义可见性、权益与运营标记，并向 kongming 货架输出可缓存的目录快照。

## ADDED Requirements

### Requirement: 目录条目与引用

控制面 SHALL 维护目录条目，引用运行时 Bot 标识；条目 SHALL 定义可见性、权益与运营标记；SHALL NOT 复制 Bot 定义/版本；引用失效 SHALL 显示不可用。

#### Scenario: 创建目录条目

- WHEN ADMIN 引用 bot b1
- THEN 条目落库，快照版本递增

#### Scenario: 引用失效

- GIVEN Bot 已删除
- WHEN 读取目录
- THEN 条目显示不可用，不伪造内容

### Requirement: 目录快照与权益

目录快照 SHALL 带版本/ETag；kongming 货架 SHALL 按快照展示可见条目；权益不满足 SHALL 隐藏或拒绝运行；目录变更 SHALL 审计。

#### Scenario: 租户可见性

- WHEN 租户拉取目录
- THEN 只返回其权益内条目

#### Scenario: 权益拒绝

- GIVEN 租户无条目权益
- WHEN 尝试运行
- THEN 拒绝，审计记录
