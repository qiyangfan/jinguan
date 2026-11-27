# Spec Delta

## Purpose

控制面连续性：v1 以单实例 + 共享 PG + 备份/恢复为基线，并定义控制面抗滥用与告警投递，保证可恢复、可限流、可告警；多实例 HA/故障切换为后续触发项。

## ADDED Requirements

### Requirement: 控制面持久性与连续运行

v1 控制面 SHALL 单实例运行并共享权威 PG；快照/命令/审计 SHALL 持久化；调度器 SHALL 单实例或租约执行，SHALL NOT 重复处理同一任务；多实例 API SHALL NOT 作为 v1 验收前置。

#### Scenario: 单实例重启

- GIVEN 控制面重启
- WHEN 服务重试拉取
- THEN 快照/命令/审计仍可读，版本不回退

#### Scenario: 调度器不重复

- GIVEN 恢复/重启
- WHEN 调度器再次执行
- THEN 同一任务不重复处理

### Requirement: 备份与恢复

控制面 SHALL 提供备份/恢复流程；恢复后快照版本 SHALL 单调不回退；命令 pending SHALL 可继续；恢复演练 SHALL 有记录；RPO/RTO SHALL 明确；备份保留期 SHALL 显式配置并清理过期备份。

#### Scenario: 恢复

- GIVEN 控制面库备份
- WHEN 恢复到新实例
- THEN 版本/命令/审计一致

#### Scenario: 版本不回退

- GIVEN 恢复旧备份
- WHEN 服务拉取
- THEN 拒绝旧版本覆盖

### Requirement: 备份安全

控制面备份 SHALL 加密存储；备份访问 SHALL 使用独立凭证与最小权限；恢复 SHALL 需二次确认/审批并审计（actor/backup_id/target/result）；备份 SHALL NOT 放仓库或公开桶；备份/恢复日志 SHALL NOT 含敏感值。

#### Scenario: 备份加密

- WHEN 生成控制面备份
- THEN 静态加密，仅备份凭证可读

#### Scenario: 未授权恢复

- WHEN 无审批/凭证尝试恢复
- THEN 拒绝并审计

#### Scenario: 恢复审计

- WHEN 授权恢复
- THEN 记录 actor/backup_id/target/result

### Requirement: 控制面抗滥用

控制面 SHALL 对所有接口施加 per-service/tenant/IP 速率、并发、请求大小与超时限制；超限 SHALL 返回 429/413/504 并带 Retry-After；限值 SHALL 可配置；限流/认证异常 SHALL 审计并可告警；SHALL NOT 让控制面限流影响服务热路径。

#### Scenario: 速率超限

- WHEN 服务/租户/IP 超过速率
- THEN 429 + Retry-After，不落库

#### Scenario: 请求过大/超时

- WHEN 请求超过大小或超时
- THEN 413/504，资源不失控

#### Scenario: 异常审计

- WHEN 限流或认证失败发生
- THEN 记录来源/结果，可告警，不含敏感值

### Requirement: 告警投递

控制面 SHALL 将控制面不可用/DB/备份失败、saga blocked、身份吊销、契约漂移、限流/认证异常投递到外部通道；告警 SHALL 带 id/severity/source/type/tenant/service/observed_at/dedup_key/runbook；SHALL 去重、重试并支持 ack；SHALL NOT 阻塞控制写；SHALL NOT 含敏感值。

#### Scenario: 告警送达

- WHEN 控制面 DB 或备份失败
- THEN 告警投递到配置通道，含 runbook

#### Scenario: 去重与重试

- WHEN 同一告警重复触发或通道暂时失败
- THEN 按 dedup_key 合并并重试，不刷屏

#### Scenario: 不泄露

- WHEN 生成/投递告警
- THEN 无 token/密码/连接串/服务域配置

### Requirement: 告警通道安全

Webhook SHALL 使用签名/共享密钥；目标 URL SHALL 走 allowlist；email SHALL 使用独立认证；通道凭证 SHALL NOT 入库/日志；重放/伪造 SHALL 拒绝；通道失败 SHALL 重试并审计。

#### Scenario: 签名校验

- WHEN 接收方收到 webhook
- THEN 校验签名，失败拒绝

#### Scenario: 目标限制

- WHEN 配置告警目标
- THEN 只允许 allowlist 内 URL/域名

#### Scenario: 凭证不泄露

- WHEN 记录投递日志/审计
- THEN 无 webhook secret/邮件凭证
