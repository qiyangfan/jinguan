## Purpose

用户体系与会话:注册/登录(JWT 无状态)、角色(首用户 ADMIN)、`@AuthenticationPrincipal` 注入 uid。当前为单 access token 形态。

## Requirements

### Requirement: 注册与角色引导

注册必须 (MUST) 校验用户名 3-64 字符、密码 8-72 字符;密码必须 (MUST) 经 BCrypt 哈希落库;首个注册用户必须 (MUST) 自动获得 ADMIN 角色,其余为 USER。

#### Scenario: 首用户即管理员
- **WHEN** 空库上第一个用户完成注册
- **THEN** 该用户 role=ADMIN,后续注册用户 role=USER

### Requirement: 登录签发 JWT

登录成功必须 (MUST) 签发 HS256 对称签名 JWT(claims:username/uid/role),TTL 可配(`jinguan.security.jwt-ttl-minutes`,默认 720 分钟);密钥经环境变量注入。

#### Scenario: 过期 token 转匿名
- **WHEN** 携带过期或非法 JWT 访问受保护端点
- **THEN** 请求以匿名身份处理,受保护端点返回 401/403

### Requirement: 角色强制三层

ADMIN 端点必须 (MUST) 经 URL 层(`/admin/**`)+ 方法层 `@PreAuthorize` 双重强制;前端路由守卫仅为体验层,不作为安全边界。

#### Scenario: USER 访问管理端点
- **WHEN** USER 角色调用 `POST /wallet/admin/credit`
- **THEN** 返回 403

### Requirement: 双 token 会话

登录必须 (MUST) 签发双令牌:access JWT(默认 30 分钟,响应体下发,前端仅存内存)承担全部 API 认证;refresh JWT(默认 7 天)必须 (MUST) 经 `Set-Cookie`(httpOnly、SameSite=Strict、Path=/auth)下发,仅用于 `POST /auth/refresh` 轮换换发新 access 与新 refresh;`POST /auth/logout` 必须 (MUST) 清除 cookie。refresh 轮换不做复用检测(登记为已知限制;出现"立即全端踢出"需求时再立会话版本号)。

#### Scenario: 页面刷新不掉登录
- **WHEN** 用户关闭标签页后次日重新打开控制台
- **THEN** 应用挂载时经 refresh cookie 静默换发新 access,会话无感恢复,localStorage 中不存在任何 token

#### Scenario: access 泄露敞口收敛
- **WHEN** 某 access token 被窃取
- **THEN** 其最长有效寿命为 30 分钟,且 JS 无法读到 refresh 凭证

### Requirement: 登录防爆破

`/auth/login` 与 `/auth/register` 必须 (MUST) 经 per-IP 固定窗口限流(缺省 login 10 次/分钟、register 3 次/分钟),超限返回 429 ProblemDetail;IP 解析必须 (MUST) 仅在显式配置可信反代时才采信 X-Forwarded-For。

#### Scenario: 撞库限速
- **WHEN** 同一 IP 一分钟内第 11 次尝试登录
- **THEN** 返回 429,窗口滑过后恢复

#### Scenario: 登录提示不可枚举
- **WHEN** 分别以「不存在的用户名」与「存在但密码错误」尝试登录
- **THEN** 两者返回相同状态码与相同提示(不泄露用户名存在性;注册时的「用户名已存在」不可避免,可接受)

### Requirement: 管理员重置密码

ADMIN 必须 (MUST) 能经 `POST /admin/users/{id}/reset-password` 重置任意用户密码(密码约束与注册一致,BCrypt 落库);重置后旧密码必须 (MUST) 立即失效。

#### Scenario: 忘密码通道
- **WHEN** 用户忘密码,ADMIN 重置为新密码
- **THEN** 旧密码登录失败,新密码登录成功
