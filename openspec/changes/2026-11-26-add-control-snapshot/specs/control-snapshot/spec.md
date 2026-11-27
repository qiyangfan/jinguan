# Spec Delta

## Purpose

控制面的共享控制快照契约：版本、ETag、按服务投影、服务认证/签名、last-known 缓存与安全类 fail-closed，让服务不依赖控制面启动。

## ADDED Requirements

### Requirement: 快照组装与版本

控制快照 SHALL 带版本/ETag/observed_at，并按服务投影；版本 SHALL 单调；旧版本 SHALL NOT 覆盖新版本；快照 SHALL NOT 含凭据；快照 SHALL 经 TLS 提供并由锦官签名，服务 SHALL 验签；匿名或未签名快照 SHALL 被拒绝。

#### Scenario: 拉取快照

- WHEN 服务带 ETag 拉取
- THEN 版本不变返回 304，变化返回新快照

#### Scenario: 旧版本拒绝

- GIVEN 服务持有 v5
- WHEN 收到 v4
- THEN 拒绝覆盖，保留 v5

#### Scenario: 未认证/签名失败

- GIVEN 请求无服务身份或签名无效
- WHEN 服务尝试应用
- THEN 拒绝，保留 last-known

### Requirement: last-known 与 fail-closed

服务 SHALL 缓存 last-known 快照；控制面不可用 SHALL NOT 阻止服务启动；安全类（租户/RBAC/共享策略/吊销/权益）快照缺失/非法 SHALL fail-closed，非安全类 SHALL 使用 last-known 并标 stale；安全类清单 SHALL 固定并契约测试。

#### Scenario: 控制面缺席

- GIVEN 控制面不可用
- WHEN 服务启动
- THEN 用 last-known 启动，标 stale

#### Scenario: 安全类非法

- GIVEN 租户/策略/吊销快照非法
- WHEN 服务加载
- THEN 拒绝，保留 last-known

#### Scenario: 非安全类陈旧

- GIVEN 价目/拓扑快照陈旧但签名有效
- WHEN 服务运行
- THEN 使用 last-known，标 stale 并告警
