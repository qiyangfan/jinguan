# 控制面连续性与告警运行手册（Control HA Runbook）

> 适用：锦官控制面 v1 部署（单实例 + 权威 PG）。多实例 HA 不在 v1 验收，触发条件见 §7。

## 1. 基线

- **拓扑**：单实例 API + 单一权威 PostgreSQL；共享控制/命令/审计全部持久化在 PG，
  控制面不依赖实例内存（可重启）。
- **服务独立性**：控制面短时不可用只影响变更与命令下发；服务用 last-known 快照继续运行，
  安全类快照缺失/非法时 fail-closed。
- **调度任务**（均带 `scheduler_leases` 租约，单实例执行，重启可续）：

| 任务 | 租约名 | 内容 |
|---|---|---|
| 审计保留 | `audit-retention` | 在线 ≥180 天后归档到 `control_audit_archive` 并删除在线行 |
| 服务注册清理 | `registry-stale-purge` | stale 超保留期删除，活跃不删 |
| 命令超时 | `command-timeout-sweep` | 领取超时回队列或进死信 |
| 命令清理 | `command-retention` | 只清终态命令，未 ack/pending 不删 |
| 用量聚合/清理 | `usage-aggregation`、`usage-raw-retention` | 日聚合；只清已聚合原始事件 |
| 投影清理 | `projection-cleanup` | 过期 last-known 清理并审计 |
| 流程推进/清理 | `saga-advance`、`saga-retention` | 推进 pending/running；未关闭 blocked 不删 |
| 告警投递/清理 | `alert-delivery`、`alert-retention` | 去重重试投递；终态记录保留期清理 |

## 2. 备份与恢复

### 2.1 备份

```bash
export JINGUAN_BACKUP_PASSPHRASE='...'   # 只从 Secret 注入,禁止入库/命令行
export PGDATABASE=jinguan PGHOST=... PGUSER=... PGPASSWORD=...
bash scripts/control-backup.sh /backup/jinguan-$(date +%F).dump.enc
```

- 归档格式：`pg_dump --format=custom` → `openssl enc -aes-256-cbc -pbkdf2`；
  产出 `.sha256` 校验文件。
- **加密存储**：备份文件静态加密；口令独立于数据库凭据；建议云 KMS 托管口令。
- **最小权限**：备份使用只读专用角色（`pg_read_all_data` + `pg_dump` 所需），
  与运行账号分离；备份桶/目录不入仓库、不公开。
- **保留**：`JINGUAN_BACKUP_RETENTION_DAYS`（默认 30 天）到期由脚本删除旧文件；
  建议外部监控对脚本退出码与最后备份时间告警（失败会调用
  `POST /control/v1/alerts/operational` 报 `BACKUP_FAILURE`）。

### 2.2 恢复

1. 取得审批单号（写入 `RESTORE_APPROVAL_ID`），复核 `PGDATABASE` 指向恢复目标。
2. 执行（破坏性，需显式确认）：

```bash
export JINGUAN_BACKUP_PASSPHRASE='...' PGDATABASE=jinguan-restore
export RESTORE_APPROVAL_ID=CHG-2026-1234 RESTORE_CONFIRM=restore-now
bash scripts/control-restore.sh /backup/jinguan-2026-11-27.dump.enc
```

3. 脚本使用 `pg_restore --clean --if-exists` 覆盖目标库，并在恢复后向
   `control_audit_log` 写入 `RESTORE` 审计（actor=审批单号）。
4. 恢复后核对：快照版本不回退（服务拒绝旧版本）、pending 命令可继续 claim、
   审计记录可查；随后重启控制面实例（版本水位从库读取）。

### 2.3 RPO / RTO

- **RPO**：默认每日全量备份 → ≤24h；提高要求时改为 6h/1h 定时备份（仅改调度，不改代码）。
- **RTO**：单实例重建 + 恢复目标 ≤ 2h（含解密/恢复/冒烟）。
- **演练记录**：见 §9 表；每次演练记录日期、备份文件、恢复时长与核对结果。

## 3. 告警

### 3.1 事件类型

| type | 触发 | 建议处置 |
|---|---|---|
| `CONTROL_UNAVAILABLE` | 外部探活发现控制面不可达 | 查进程/DB；服务 last-known 继续 |
| `DB_FAILURE` | DB 连接/迁移失败、写入失败 | 查 PG 与连接池；恢复后核对版本水位 |
| `BACKUP_FAILURE` | 备份脚本失败 | 查磁盘/口令/权限，重跑备份 |
| `SAGA_BLOCKED` | 补偿失败/服务长时间无回执 | 查流程步骤与命令，修复后人工关闭 |
| `IDENTITY_REVOKED` / `AUTH_FAILURE` | 服务身份吊销/认证拒绝 | 核对身份目录与 Keycloak client |
| `CONTRACT_DRIFT` | X-01 harness 失败/契约漂移 | 阻断合并，按契约报告修复字段/版本 |
| `RATE_LIMITED` | 超 per-service/tenant/IP 限流 | 查来源；必要时调 `JINGUAN_CONTROL_RATE_LIMIT` |

### 3.2 投递与安全

- 通道：webhook（必需 allowlist + HMAC-SHA256 签名）与/或 email（独立 `spring.mail` 认证）。
  未配置通道时告警记录标记 FAILED 以便排查，不静默丢弃。
- **防重放**：webhook 头 `X-Jinguan-Timestamp`（epoch 秒）参与签名，签名原文为
  `"{timestamp}.{raw_body}"`；接收方须校验 HMAC 并拒绝时间戳偏差超过 ±300s 的请求，
  同一 `dedup_key`/`id` 重复投递应幂等忽略。
- 去重：`dedup_key` + `JINGUAN_ALERT_DEDUP_SECONDS`（默认 900s）；重试直到
  `JINGUAN_ALERT_MAX_ATTEMPTS`；成功后 `DELIVERED`，人工确认置 `ACKED`。
- 载荷经脱敏（password/token/secret/credential/api_key/connection/dsn → `***`），
  不含凭据；审计记录 `ALERT_RAISE`/`ALERT_ACK`/`ALERT_PURGE`。
- 运维上报：`POST /control/v1/alerts/operational`，头 `X-Operational-Token`
  （`JINGUAN_ALERT_REPORT_TOKEN`）；仅允许 CONTROL_UNAVAILABLE/DB_FAILURE/BACKUP_FAILURE/CONTRACT_DRIFT。
- 接收方校验签名示例：`hmac_sha256(secret, raw_body)` 与头内 `sha256=<hex>` 比较；
  失败拒绝。allowlist 之外的 URL 在启动时即拒启。

## 4. 抗滥用

- `/control/**`、`/ingest/**`、`/admin/**` 统一限流：per-service（`X-Service-Id` 或
  principal）/租户/IP，固定 60s 窗口；超限 `429` + `Retry-After: 60` 并审计
  `RATE_LIMITED`。
- 请求体上限 `JINGUAN_CONTROL_MAX_REQUEST_BYTES`（默认 1 MiB）→ `413`。
- 出站只读投影超时 → 控制台显示 stale/unknown；不阻塞共享控制写。
- 限流不影响服务热路径（服务按 last-known 运行，无需实时调用锦官）。

## 5. 故障矩阵（v1）

| 故障 | 服务 | 锦官控制台 | 处理 |
|---|---|---|---|
| 停锦官 | last-known 运行 | 不可用 | 恢复实例；无数据丢失（PG 持久） |
| 停服务 | 正常 | 服务页 stale/unknown | 服务恢复后自动重拉投影/心跳 |
| 停 Keycloak | 已持 token 到期后无法续 | 用户登录失败；服务身份 managed 校验失败 | 恢复 IdP；不改变本地授权数据 |
| DB 故障 | last-known 运行 | 读写失败 | 恢复 DB/切换副本；版本从库读 |
| 命令服务宕机 | 命令 PENDING | 命令显示 pending | 服务恢复 claim；超时进死信 |

## 6. 契约回滚

1. 破坏性契约变更先升版本并双方同步；`catalog.json` 记录哈希与版本。
2. 回滚：恢复上一版本 schema/示例与生成客户端，保留上一服务端兼容窗口（旧字段仍可读）。
3. 演练：在测试环境用上一 catalog 哈希跑 `ControlContractHarnessTest`，全绿后切回。

## 7. 多实例 HA（未启用，触发项）

v1 不部署多实例。出现以下任一条件时另立 change 评审：持续写负载超过单实例容量、
RTO 要求 < 2h、跨可用区容灾要求、命令吞吐成为瓶颈。启用前必须先实现副本协调
（调度器租约已就绪，API 无状态化、会话/限流共享需补）。

## 8. 变更记录

- 2026-11-27：建立 v1 基线与本 runbook；备份/恢复脚本入库；告警/限流/租约落地。

## 9. 演练记录

| 日期 | 项目 | 结果 |
|---|---|---|
| 2026-11-27 | 备份/恢复流程评审（脚本 + 审批 + 审计路径） | 静态评审通过；真实演练需在有备份存储的测试环境执行并回填本表 |
