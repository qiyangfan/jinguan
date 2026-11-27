# Spec Delta

## Purpose

锦官控制面的跨仓契约测试门禁：统一快照、命令、用量/健康、服务只读 API/投影与服务注册契约的 schema、示例与生成客户端，并以必跑测试阻止三仓 wire 契约漂移。

## ADDED Requirements

### Requirement: 契约单一来源与版本

jinguan SHALL 维护控制面契约 schema/示例/生成客户端版本；快照/命令 envelope/命令 action payload/用量/健康/服务只读 API/投影/服务注册 SHALL 使用同一份契约；每个 action 的 params schema SHALL 有独立版本；破坏性变更 SHALL 升版本并要求服务侧同步；契约 SHALL NOT 含服务域配置或凭据。

#### Scenario: 契约生成

- WHEN 契约变更合并
- THEN 三仓生成客户端来自同一版本且无漂移

#### Scenario: 破坏性变更

- WHEN 删除/改名必填字段
- THEN 版本升级，服务侧同步后才可合并

#### Scenario: 敏感字段

- WHEN 检查契约 schema/示例
- THEN 无凭据/连接串/服务域配置字段

#### Scenario: action 漂移

- WHEN action 名称/params/版本与契约不一致
- THEN 契约测试失败，阻断合并

### Requirement: 跨仓契约测试门禁

三仓 SHALL 在 CI/本地门禁运行跨仓契约测试，覆盖 schema 校验、生成物无漂移、provider/consumer 兼容与认证负向；失败 SHALL 阻断合并；X-01 owner SHALL 为本 change；SHALL NOT 以“适用时”跳过快照/命令/用量/只读 API/注册契约。

#### Scenario: 契约漂移

- WHEN provider 与 consumer 字段/方向/错误码不一致
- THEN 契约测试失败，阻断合并并报告差异

#### Scenario: 认证负向

- WHEN 匿名/未签名/越权调用
- THEN 契约测试断言 401/403，不接受降级

#### Scenario: 跳过门禁

- WHEN 提交尝试以“适用时”跳过五类契约
- THEN 门禁拒绝，要求必跑

### Requirement: 失败报告与回滚

契约测试失败 SHALL 报告字段、方向、契约版本与期望/实际；发布 SHALL 保留上一契约版本的回滚路径；SHALL NOT 通过放宽断言或删除测试修复漂移。

#### Scenario: 字段漂移

- WHEN 必填字段缺失或类型变化
- THEN 报告字段/方向/版本与期望/实际

#### Scenario: 回滚

- WHEN 新契约版本导致不兼容
- THEN 回滚到上一版本并保持旧客户端可用
