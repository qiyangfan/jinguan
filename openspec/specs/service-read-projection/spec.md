# service-read-projection Specification

## Purpose

管理域对服务数据的只读投影契约：经服务只读 API/投影获取配置与运行时摘要，缓存 last-known，带 observed_at/stale/ETag，不直连库、不代理写，保证独立部署。

## Requirements

### Requirement: 只读投影与缓存

管理域 SHALL 经服务只读 API/投影读取服务配置与运行时摘要；投影 SHALL 带 source/observed_at/stale/ETag；锦官 SHALL 缓存 last-known；服务不可用 SHALL 使用 last-known 并标 stale；无投影 SHALL 显示未知，不得伪造；SHALL NOT 直连服务 DB、跨库 JOIN 或 CDC。

#### Scenario: 正常读取

- WHEN 服务只读 API 返回投影
- THEN 锦官更新 last-known，显示 observed_at

#### Scenario: 服务宕机

- GIVEN 服务不可用
- WHEN 打开服务只读页
- THEN 显示 last-known 并标 stale，不伪造实时

#### Scenario: 无投影

- GIVEN 从未收到投影
- WHEN 打开服务页
- THEN 显示未知/不可用，不编造数据

#### Scenario: 禁止直连库

- WHEN 实现读取路径
- THEN 只走服务只读 API/投影，无服务 DB 连接/跨库查询

### Requirement: 投影保留与清理

锦官 SHALL 为投影缓存设置 stale 阈值与保留期；超过保留期 SHALL 清理/归档，不保留敏感值；清理 SHALL 审计；服务恢复后 SHALL 重新拉取；SHALL NOT 把过期投影当实时。

#### Scenario: 投影过期

- WHEN 投影超过保留期
- THEN 清理/归档，页面显示未知或重新拉取

#### Scenario: 清理审计

- WHEN 清理 job 执行
- THEN 记录 service_id/范围/结果，可追责

#### Scenario: 无敏感残留

- WHEN 清理完成
- THEN 无凭据/token/密钥残留

### Requirement: 服务地址来源

服务只读 API base URL SHALL 来自锦官部署配置的 service_id→base_url 静态映射；SHALL NOT 从服务注册表发现 endpoint；缺失映射 SHALL 显示未知/不可用，不猜测；静态映射 SHALL 支持独立部署。

#### Scenario: 静态映射命中

- WHEN 锦官配置了 service_id 的 base URL
- THEN 只读客户端调用该地址，不查询注册表

#### Scenario: 缺失映射

- GIVEN 锦官未配置 service_id 的 base URL
- WHEN 打开服务只读页
- THEN 显示未知/不可用，不猜测地址

#### Scenario: 禁止 endpoint 发现

- WHEN 实现地址解析
- THEN 不读取注册 payload 的 endpoint，不做服务发现

### Requirement: 认证与脱敏

服务只读 API SHALL 要求服务身份/管理员只读；字段 SHALL 白名单脱敏；SHALL NOT 返回凭据/token/密码/密钥/连接串；锦官缓存 SHALL NOT 落敏感值。

#### Scenario: 未认证拒绝

- WHEN 无有效只读身份
- THEN 401/403，不返回数据

#### Scenario: 敏感值不返回

- WHEN 读取含凭据字段的配置
- THEN 返回脱敏占位，不返回原值

### Requirement: 敏感只读审计

锦官 SHALL 对敏感服务配置详情查看/导出记录审计（actor/tenant/service/object/action/result/request_id）；普通列表/健康摘要 SHALL NOT 写审计；审计 SHALL NOT 含字段值/凭据。

#### Scenario: 详情/导出审计

- WHEN ADMIN 查看敏感服务配置详情或导出
- THEN 记录 actor/object/result，无字段值

#### Scenario: 普通列表不记

- WHEN ADMIN 浏览普通列表/健康摘要
- THEN 不写审计

#### Scenario: 审计不泄露

- WHEN 写入只读审计
- THEN 无凭据/token/密钥/连接串

### Requirement: 独立部署

停锦官 SHALL NOT 阻止服务启动/运行；停服务 SHALL NOT 阻止锦官共享控制；服务 schema 变更 SHALL NOT 要求锦官迁移；上述行为 SHALL 以 last-known/投影缓存实现，不得靠直连库。

#### Scenario: 停锦官

- GIVEN 锦官不可用
- WHEN 服务启动
- THEN 服务用 last-known 启动并运行核心流程

#### Scenario: 停服务

- GIVEN 服务不可用
- WHEN 打开锦官控制台
- THEN 共享控制可用，服务页读最后投影并标 stale

#### Scenario: schema 演进

- GIVEN 服务内表结构变更
- WHEN 只读 API 契约不变
- THEN 锦官无需迁移或跨库适配
