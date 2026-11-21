## Purpose

锦官本地会话与授权主体:人类身份只来自虎符 OIDC `sub`,本地只保存角色、禁用与停服;签发 RS256 双 token,refresh 轮换/重用检测;不提供本地密码、注册或改密。

## Requirements

### Requirement: 无本地账号

锦官不得 (MUST NOT) 提供注册、密码登录、改密或本地管理员账密;所有人类登录必须 (MUST) 经虎符 OIDC;主体主键必须 (MUST) 为虎符 `sub`。

#### Scenario: 本地登录拒绝

- **WHEN** 调用旧 `/auth/login` 或 `/auth/register`
- **THEN** 不存在该端点

#### Scenario: subject 外键

- **WHEN** 账本/订单/API key/refresh/用量写入
- **THEN** 归属列为虎符 subject,且引用 `principals(subject)`

### Requirement: OIDC RP 安全

授权必须 (MUST) 使用授权码 + PKCE(S256);回调必须 (MUST) 校验 state 单次、nonce、iss/aud/exp 与 JWKS 签名;state 必须 (MUST) 绑定发起浏览器;错误不得 (MUST NOT) 回显 token/code/secret。

#### Scenario: state 重放

- **GIVEN** 已完成一次回调
- **WHEN** 重复使用同一 state
- **THEN** 拒绝

#### Scenario: 登录 CSRF

- **WHEN** 回调缺少与 state 匹配的浏览器 cookie
- **THEN** 拒绝

### Requirement: 授权留本地

首登只创建无权限 USER 资料;角色/钱包/停服必须 (MUST) 由锦官管理员显式授予;不得 (MUST NOT) 从 token 接收产品角色、租户或钱包。

#### Scenario: 首登无权限

- **WHEN** 新 subject 首次登录
- **THEN** 建立 USER 资料且无钱包/管理权限

### Requirement: 登录签发 JWT

登录成功必须 (MUST) 经 RS256 非对称签名签发 token;claims 必须 (MUST) 含 iss/aud/sub/role/typ;不得 (MUST NOT) 含本地 `uid`;公钥必须 (MUST) 经 JWKS 发布。

#### Scenario: 过期 token 转匿名

- **WHEN** 携带过期或非法 token 访问受保护端点
- **THEN** 请求以匿名身份处理,受保护端点返回 401/403

### Requirement: 角色强制三层

ADMIN 端点必须 (MUST) 经 URL 层(`/admin/**`)+ 方法层 `@PreAuthorize` 双重强制;前端路由守卫仅为体验层。

#### Scenario: USER 访问管理端点

- **WHEN** USER 调用 `POST /wallet/admin/credit`
- **THEN** 返回 403

### Requirement: 双 token 会话

登录必须 (MUST) 签发 access(默认 30 分钟,响应体,前端仅存内存)与 refresh(默认 7 天,httpOnly cookie)。refresh 轮换必须 (MUST) 原子消费并保留 30 秒并发宽限;超窗重放必须 (MUST) 吊销该 subject 全家族。`POST /auth/logout` 与 `/auth/logout/sso` 必须 (MUST) 真吊销。

#### Scenario: 页面刷新不掉登录

- **WHEN** 用户重新打开控制台
- **THEN** 经 refresh cookie 静默换发新 access,localStorage 无 token

#### Scenario: 旧 refresh 重放吊销家族

- **GIVEN** 某 refresh 已轮换且超出宽限
- **WHEN** 旧 refresh 再次提交
- **THEN** 吊销该 subject 全家族

#### Scenario: 并发续期不互踢

- **GIVEN** 同一 refresh 被两个并发请求提交
- **WHEN** 均在宽限窗口内
- **THEN** 两请求成功且返回同一后继会话

### Requirement: 登录入口限流

`/auth/oidc/start`、`/auth/oidc/callback` 与 `/auth/break-glass` 必须 (MUST) 经 per-IP 固定窗口限流;超限返回 429;IP 解析仅在显式可信反代时采信 X-Forwarded-For。

#### Scenario: 撞库限速

- **WHEN** 同一 IP 一分钟内超过阈值调用 OIDC 入口
- **THEN** 返回 429

### Requirement: 账号禁用与停服

禁用(disabled)必须 (MUST) 独立于欠费停服(`suspended_at`):禁用拒绝 refresh 与后续管理操作,并吊销 refresh 家族;停服仅切数据面用量,不阻断既有 token。

#### Scenario: 禁用拒续期

- **WHEN** disabled 主体提交 refresh
- **THEN** 返回 403

#### Scenario: 欠费停服仍持 token

- **WHEN** `suspended_at` 置位
- **THEN** 既有 access token 与续期继续可用,数据面用量经吊销名单切断

### Requirement: break-glass

break-glass 必须 (MUST) 默认关闭、凭据只从环境变量注入、签发短时 ADMIN、仅限运维、逐次审计;不得 (MUST NOT) 创建普通账号或长期会话。

#### Scenario: 默认关闭

- **WHEN** 未配置 break-glass 凭据
- **THEN** `/auth/break-glass` 返回 404

#### Scenario: 审计

- **WHEN** 正确凭据签发成功
- **THEN** 落一条 break-glass 审计,含主体、来源 IP 与有效期

### Requirement: 签名密钥持久化与轮换

生产签发私钥必须 (MUST) 经挂载 PKCS#8 文件提供;kid 必须 (MUST) 取 RFC 7638 公钥指纹;JWKS 必须 (MUST) 同时发布 active 与退役公钥,退役公钥保留至其签发 token 全部过期。

#### Scenario: 轮换后旧 token 仍可验签

- **WHEN** 旧键退役后携旧 refresh 续期
- **THEN** 验签通过直至 token 过期
