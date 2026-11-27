# 服务身份运行手册（Service Identity Runbook）

> 适用：锦官控制面 ↔ kongming/muniu 的机器身份。Keycloak（符节/fujie realm）是身份权威；
> 锦官维护身份目录、校验与审计。用户 SSO 不在此文。

## 1. 身份目录

| client_id | service_id | 方向 | scope | 用途 |
|---|---|---|---|---|
| `kongming-control` | kongming | 服务 → 锦官 | registry,snapshot,command,usage | 注册/心跳/快照/命令/用量 |
| `muniu-control` | muniu | 服务 → 锦官 | registry,snapshot,command,usage | 同上 |
| `jinguan-read-kongming` | kongming | 锦官 → 服务 | read | 只读投影拉取 |
| `jinguan-read-muniu` | muniu | 锦官 → 服务 | read | 只读投影拉取 |

配置位于 `application.yml` 的 `jinguan.service-auth`（managed 模式）与
`jinguan.services.read`（出站凭据）。目录只读展示：`GET /admin/service-identities`（ADMIN）。

## 2. 模式

- **managed（生产）**：`JINGUAN_SERVICE_AUTH_MODE=managed` +
  `JINGUAN_SERVICE_AUTH_ISSUER`、`JINGUAN_SERVICE_AUTH_JWK_SET_URI`。
  服务用 Keycloak client credentials 取短时 token；锦官按 issuer/JWKS/`azp`/scope 校验。
  token 缺失或验签失败 401，未知/吊销 client 或无 scope 403，并写审计与告警（去重）。
- **local（仅开发）**：`JINGUAN_SERVICE_AUTH_LOCAL_TOKEN`（可用
  `JINGUAN_SERVICE_AUTH_LOCAL_TOKEN_PREVIOUS` 做重叠轮换）；只接受 loopback 请求，
  远程一律 403。pre-shared 不得进入 managed 环境。

## 3. 开通（fujie 侧前置）

1. 在符节 realm 创建 machine client（confidential，service accounts enabled），
   关闭用户登录/重定向；按上表授予 client scope（`registry`/`snapshot`/`command`/`usage`/`read`）。
2. client secret 只注入服务运行环境（K8s Secret / systemd EnvironmentFile），
   **不得进入仓库、命令行参数、日志或审计**。
3. 锦官侧只在身份目录登记 client_id → service_id/scope；不持有服务 secret。
4. 冒烟：fujie `scripts/service-token-smoke.sh`（该仓实现）→ 服务注册 200、快照 200、
   匿名 401、缺 scope 403。

> 当前批次阻塞：fujie 尚未创建上述 client（跨仓依赖），锦官侧以 local 模式 + 目录/校验/
> 审计/runbook 就绪；client 创建后切 managed 并跑冒烟。

## 4. 轮换

- **服务 token**：Keycloak 短时 token 自动轮换；无需锦官操作。
- **client secret（managed）**：Keycloak 支持双 secret 重叠窗口；先加新 secret →
  服务滚动注入 → 观察鉴权成功 → 移除旧 secret。轮换不停机。
- **本地 pre-shared（仅开发）**：新值写入 `local-token`，旧值放
  `local-token-previous`；窗口结束清空 previous。
- **快照签名密钥**：`active.pem` 轮换时旧公钥移入 `retired/`，JWKS 同时发布新旧公钥；
  服务缓存 JWKS 直至旧 key 退出（旧快照签名验证不受影响）。详见
  `runbook-single-identity.md`。

## 5. 吊销与失败语义

1. 泄露/停用：Keycloak 禁用 client（或删除 role/scope）→ 新 token 立即失效；
   已有 token 至多存活一个 TTL（建议 ≤5 分钟）。
2. 锦官侧同步把目录项标记 `disabled: true`（配置变更走代码评审）→ 立即 403，
   不等 token 过期。
3. 吊销后预期：注册/心跳/快照/claim/ack/用量均 401/403；审计
   `SERVICE_AUTH_REJECT`，告警 `AUTH_FAILURE`（dedup 15 分钟窗口）。
4. 服务身份缺失时：服务可用 last-known 快照运行，但不得注册/上报/claim；
   锦官共享控制台与 break-glass 不受影响。
5. 吊销演练记录：在隔离环境用 `disabled: true` 验证 403 与审计，再恢复配置。

## 6. 审计与告警

- 认证拒绝：`control_audit_log.action=SERVICE_AUTH_REJECT`，含 scope/mode/remote/reason，
  无凭据值。
- 告警：`AUTH_FAILURE`（WARNING）按 dedup 合并投递（webhook/email），runbook 指回本文。
- 目录只读视图不含任何 secret/token；如需核对 secret 是否泄露，查服务侧 Secret 与日志，
  不要从锦官读取。
