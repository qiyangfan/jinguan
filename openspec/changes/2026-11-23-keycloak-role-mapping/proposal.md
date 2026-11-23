# 提案:粗角色改由 Keycloak client roles 提供(白名单映射)

## Why

- 跨仓 ADR-0002 / IAM-TARGET 已定:**粗粒度产品角色归 Keycloak**;锦官现行规格
  (auth-session / identity-authority 的「授权留本地」)要求 MUST NOT 从 token 接收角色,
  是旧 IdP 时代的过度概括(旧 IdP 已退役)。
- 该条款保护的核心必须保留:租户、钱包、权限点、停服语义不得来自 token。但把 `ADMIN/USER`
  也一并禁止,导致身份权威分裂(Keycloak 管是谁、锦官管是不是管理员)、两套管理面、
  无法承接 IdP 组/SCIM/组织化角色。
- RP 已强校验 iss/aud/JWKS 与 azp;角色带来的风险核心是**未白名单过滤**,不是"来自 token"。
- 被否决备选:保持本地角色(双轨、不可演进);"角色 OR 本地白名单"双轨(权威不清);
  realm roles(单 realm 多产品角色泄漏);Keycloak Authorization Services(绑定且复杂)。

## What Changes

- 回调解析 `resource_access["jinguan-web"].roles`,白名单映射:
  `jinguan-admin → ADMIN`、`jinguan-user → USER`;其余角色丢弃;无命中按 `USER`。
- 首登时以 IdP 为准同步本地 `principals.role` 缓存;自签 token 的 `role` claim 取该缓存;
  refresh 沿用缓存,不引入 IdP 回源(避免把续期变成 IdP 强依赖)。
- 退役 `POST /admin/users/{subject}/role`(角色改由 Keycloak 管理);保留
  `disable/enable`、停服(`suspended_at`)、break-glass 与钱包/账本全部业务规则。
- platform `setup-realm.sh` 增建 client roles 并赋测试用户(跨仓任务)。
- 规格改写:「授权留本地」→「粗角色映射 + 业务授权留本地」。

## Non-goals

- 不从 token 接收租户/钱包/权限点;不做细粒度授权或 OPA;不引入 SCIM/组织同步。
- 不改 muniu/kongming 的准入模型(另行立项,沿用本 change 模式)。

## Impact

- 安全可见行为变更:角色变更在下一次**重新登录**时收敛;管理员角色授予入口从锦官
  迁移到 Keycloak。
- 内部 API 破坏性变更:`POST /admin/users/{subject}/role` 移除;前端相应操作下线。
- greenfield:重新登录即按新角色重建本地缓存,无存量迁移脚本。
- 测试:新增映射/丢弃/缺省/角色撤销收敛/租户钱包忽略用例;改写"首登恒 USER"断言。

## Rollout

1. platform 建角色并赋测试用户 → 2. 失败测试 → 3. 映射实现与端点退役 → 4. 前端与文档 →
5. `make check` + Keycloak 运行时联调验收。