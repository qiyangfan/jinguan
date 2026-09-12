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

### Requirement: 无刷新与吊销机制(现状)

当前只 (SHALL) 存在单 access token:无 refresh 端点、无登出端点、无黑名单;前端将 token 存 localStorage,登出为纯客户端清态。

#### Scenario: 现状形态
- **WHEN** 用户登录
- **THEN** 获得一个 12 小时有效的 access token,期间无法从服务端吊销

### Requirement: 角色强制三层

ADMIN 端点必须 (MUST) 经 URL 层(`/admin/**`)+ 方法层 `@PreAuthorize` 双重强制;前端路由守卫仅为体验层,不作为安全边界。

#### Scenario: USER 访问管理端点
- **WHEN** USER 角色调用 `POST /wallet/admin/credit`
- **THEN** 返回 403
