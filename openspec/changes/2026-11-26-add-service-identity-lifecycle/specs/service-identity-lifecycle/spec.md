# Spec Delta

## Purpose

锦官控制面的服务身份生命周期：为控制面与 kongming/muniu 建立机器身份目录、Keycloak client/role 开通、短时凭据、轮换与吊销，并限制本地 pre-shared 仅用于 localhost。

## ADDED Requirements

### Requirement: 服务身份目录与最小权限

jinguan SHALL 维护服务身份目录（service_id/方向/client/roles/scopes/environment）；每服务与每方向 SHALL 使用独立 Keycloak client；锦官调服务只读 API SHALL 只授只读 scope；服务调控制面 SHALL 按注册/快照/命令/用量分权；SHALL NOT 复用用户会话或密码；目录与变更 SHALL 审计。

#### Scenario: 开通服务身份

- WHEN 为 kongming 开通控制面身份
- THEN 只拿到注册/快照/命令/用量所需 scope

#### Scenario: 最小权限

- WHEN 服务身份调用未授权 scope
- THEN 401/403，不降级

#### Scenario: 不共享用户会话

- WHEN 检查服务身份
- THEN 不是用户 OIDC 会话，无用户密码

### Requirement: 短时凭据与轮换

managed SHALL 使用 Keycloak client credentials 与短时 token；SHALL NOT 在仓库或命令行存长期密钥；client secret 与签名密钥 SHALL 支持双密钥重叠轮换；旧凭据 SHALL 在窗口后失效；轮换 SHALL 不要求停服务；本地 pre-shared SHALL 仅限 localhost，远程 SHALL 拒绝。

#### Scenario: 轮换不停机

- WHEN 轮换 client secret
- THEN 新旧 secret 短暂重叠，服务不中断

#### Scenario: 旧凭据失效

- WHEN 重叠窗口结束
- THEN 旧 secret 被拒绝，新 token 正常

#### Scenario: 本地限制

- WHEN 从非 localhost 使用 pre-shared
- THEN 拒绝，不进入 managed

### Requirement: 吊销与失败语义

服务停用/泄露 SHALL 可吊销 client/role；吊销后新 token、注册、claim、上报 SHALL 拒绝；已有 token SHALL 到期失效；服务身份缺失/非法时控制操作 SHALL fail-closed，核心服务 MAY 用 last-known 运行但 SHALL NOT 伪造身份；吊销/拒绝 SHALL 审计。

#### Scenario: 吊销身份

- WHEN 吊销 kongming client
- THEN 新 token/注册/claim/上报均 401/403

#### Scenario: 身份缺失

- GIVEN managed 服务身份不可用
- WHEN 服务启动
- THEN 可 last-known 运行，但不注册/上报/claim

#### Scenario: 审计

- WHEN 身份开通/轮换/吊销/拒绝
- THEN 有 actor/原因/结果，无敏感值
