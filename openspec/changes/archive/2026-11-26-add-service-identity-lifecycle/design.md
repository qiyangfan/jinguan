# Design: 服务身份生命周期（service-identity-lifecycle）

## Context

控制面调用服务只读 API、服务调用控制面注册/快照/命令/用量，都是机器身份；现有计划只要求“服务身份认证”，没有身份目录、开通、轮换与吊销的执行面。服务身份缺失或长期密钥会直接影响安全与可运维性。

## Goals / Non-Goals

**Goals:**
- 双向机器身份可开通、最小权限、可轮换、可吊销。
- managed 短时凭据；本地 pre-shared 仅 localhost。
- 身份缺失时控制操作 fail-closed，服务仍可 last-known 运行。

**Non-Goals:**
- 不做用户 SSO/Keycloak 替代。
- 不管理服务域凭据/key 池/OAuth 账号。
- 不做长期静态 token（managed）。
- 不共享用户会话。

## Decisions

1. Keycloak 是身份权威；锦官维护服务身份目录与开通/轮换/吊销 runbook；服务 owner 消费，不各自建 client。
2. 每服务/方向独立 principal：`kongming-control`、`muniu-control`、`jinguan-read-kongming`、`jinguan-read-muniu`；只授所需 scope。
3. managed 用 client credentials + 短时 token；本地 pre-shared 仅 localhost，远程拒绝。
4. 轮换用双密钥重叠；token TTL 短；快照签名 JWKS 与服务 client secret 分开轮换；保留时钟偏差窗口。
5. 吊销 client/role 后新 token、注册、claim、上报拒绝；已有 token 到期失效；吊销/拒绝审计。
6. 服务身份缺失/非法时控制操作 fail-closed；核心服务可 last-known 运行，但不注册/上报/claim；本地管理台/break-glass 可用。
7. 密钥只经 env/secret store 注入；不进仓库、命令行、日志或审计；不共享用户会话。

## Risks / Trade-offs

- 轮换中断 → 双 secret 重叠 + 回滚旧 secret。
- 权限过大 → 每方向独立 client/scope + 负向测试。
- 吊销不彻底 → 短 TTL + 禁用 client + 审计核对。
- 本地 pre-shared 泄漏 → 仅 localhost、仅开发配置，不进入 managed。

## Migration Plan

greenfield：建立身份目录与 clients/roles；本地开发 pre-shared；无业务数据迁移；先于服务接入。
