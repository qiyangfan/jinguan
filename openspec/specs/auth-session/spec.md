## Purpose

用户体系与会话:注册/登录(RS256 无状态 JWT + JWKS 公钥发布)、角色(首用户 ADMIN,并发原子)、`@AuthenticationPrincipal` 注入 uid;双 token 形态(access 存内存 + refresh httpOnly cookie 轮换,含重用检测与并发宽限);账号禁用闸门。

## Requirements

### Requirement: 注册与角色引导

注册必须 (MUST) 校验用户名 3-64 字符、密码 8-72 字符;密码必须 (MUST) 经 BCrypt 哈希落库(Argon2/DelegatingPasswordEncoder 列为可选加固)。空库首注册必须 (MUST) 授予 ADMIN 作为 standalone 默认引导;并发首注册必须 (MUST) 原子化,不得 (MUST NOT) 因 `count()==0` 竞态产生双 ADMIN。种子管理员可作为集成部署的可选引导方式,但不替换默认路径。

#### Scenario: 首用户即管理员

- **WHEN** 空库上第一个用户完成注册
- **THEN** 该用户 role=ADMIN,后续注册用户 role=USER

#### Scenario: 并发首注册不产生双 ADMIN

- **WHEN** 两个注册请求并发到达空库
- **THEN** 恰好一个用户获得 ADMIN,不因 count 竞态产生双 ADMIN

### Requirement: 登录签发 JWT

登录成功必须 (MUST) 经 RS256 非对称签名签发 token;claims 必须 (MUST) 含 iss/aud/uid/role/typ;公钥必须 (MUST) 经 JWKS 发布(内部验签/审计用);不得 (MUST NOT) 以对称共享密钥签发(任何持密钥方均可伪造任意 uid/role)。access token 的 `sub` 语义保持现状,并恒含 `uid` claim。

#### Scenario: 过期 token 转匿名

- **WHEN** 携带过期或非法 token 访问受保护端点
- **THEN** 请求以匿名身份处理,受保护端点返回 401/403

#### Scenario: 公钥可验签

- **WHEN** 以 JWKS 公钥验证锦官签发 token
- **THEN** 验签与 iss/aud 校验通过,无需共享 secret

### Requirement: 角色强制三层

ADMIN 端点必须 (MUST) 经 URL 层(`/admin/**`)+ 方法层 `@PreAuthorize` 双重强制;前端路由守卫仅为体验层,不作为安全边界。

#### Scenario: USER 访问管理端点
- **WHEN** USER 角色调用 `POST /wallet/admin/credit`
- **THEN** 返回 403

### Requirement: 双 token 会话

登录必须 (MUST) 签发双令牌:access(默认 30 分钟,响应体下发,前端仅存内存)+ refresh(默认 7 天,httpOnly cookie)。refresh 轮换必须 (MUST) 做重用检测:旧票原子消费立墓碑;并发续期必须 (MUST) 有宽限窗口(默认 30 秒),窗口内同票重复提交返回同一后继票,不得 (MUST NOT) 吊销家族;超窗重复提交视为重放,必须 (MUST) 吊销全家族。`POST /auth/logout` 与改密必须 (MUST) 真吊销该用户全部 refresh 家族,不得 (MUST NOT) 仅清 cookie。

#### Scenario: 页面刷新不掉登录

- **WHEN** 用户关闭标签页后次日重新打开控制台
- **THEN** 经 refresh cookie 静默换发新 access,localStorage 中不存在任何 token

#### Scenario: access 泄露敞口收敛

- **WHEN** 某 access token 被窃取
- **THEN** 其最长有效寿命为 30 分钟,且 JS 无法读到 refresh 凭证

#### Scenario: 旧 refresh 重放吊销家族

- **GIVEN** 某 refresh 已轮换且已超出宽限窗口
- **WHEN** 旧 refresh 再次提交
- **THEN** 吊销该用户全家族,需重新登录

#### Scenario: 并发续期不互踢

- **GIVEN** 同一 refresh 被两个并发请求同时提交(如多标签页同时收到 401)
- **WHEN** 两请求均在宽限窗口内到达
- **THEN** 两请求均成功且返回同一后继会话,家族不被吊销

#### Scenario: logout 真吊销

- **WHEN** 用户 logout
- **THEN** 服务端吊销 refresh 家族,旧票不可再换发

### Requirement: 登录防爆破

`/auth/login` 与 `/auth/register` 必须 (MUST) 经 per-IP 固定窗口限流(缺省 login 10 次/分钟、register 3 次/分钟),超限返回 429 ProblemDetail;IP 解析必须 (MUST) 仅在显式配置可信反代时才采信 X-Forwarded-For。

#### Scenario: 撞库限速
- **WHEN** 同一 IP 一分钟内第 11 次尝试登录
- **THEN** 返回 429,窗口滑过后恢复

#### Scenario: 登录提示不可枚举
- **WHEN** 分别以「不存在的用户名」与「存在但密码错误」尝试登录
- **THEN** 两者返回相同状态码与相同提示(不泄露用户名存在性;注册时的「用户名已存在」不可避免,可接受)

### Requirement: 管理员重置密码

ADMIN 必须 (MUST) 能经 `POST /admin/users/{id}/reset-password` 重置任意用户密码(约束与注册一致);重置后旧密码必须 (MUST) 立即失效,且必须 (MUST) 连带吊销该用户全部 refresh 家族(旧 token 踢出)。

#### Scenario: 忘密码通道

- **WHEN** 用户忘密码,ADMIN 重置为新密码
- **THEN** 旧密码登录失败,新密码登录成功

#### Scenario: 重置踢下线

- **WHEN** ADMIN 重置某用户密码
- **THEN** 该用户既有 refresh 家族被吊销,在线会话需重新登录

### Requirement: 账号禁用闸门

账号禁用(disabled)必须 (MUST) 独立于欠费停服(`suspended_at`):disabled 用户 login/refresh 必须 (MUST) 被拒;`suspended_at` 仍仅切数据面用量、不阻断登录。两者语义不得 (MUST NOT) 混用。

#### Scenario: 禁用拒登

- **WHEN** disabled 用户尝试 login 或 refresh
- **THEN** 拒绝(401/403)

#### Scenario: 欠费停服仍可登录

- **WHEN** `suspended_at` 置位用户登录
- **THEN** 登录成功,可查钱包/下单;数据面用量经吊销名单切断

### Requirement: 签名密钥持久化与轮换

生产签发私钥必须 (MUST) 经挂载文件提供(未加密 PKCS#8,RSA ≥2048 位),不得 (MUST NOT) 以进程内临时密钥承担生产签发;未挂载时仅允许开发形态,并必须 (MUST) 启动告警。kid 必须 (MUST) 取 RFC 7638 公钥指纹以保证跨重启稳定;JWKS 必须 (MUST) 同时发布 active 与退役公钥,轮换时退役公钥必须 (MUST) 保留至其签发 token 全部过期(refresh TTL,默认 7 天)后方可移除。

#### Scenario: 重启不失效会话

- **WHEN** 以挂载密钥重启服务
- **THEN** 已签发 access/refresh token 仍可验签,RP 缓存 JWKS 的 kid 不变

#### Scenario: 轮换后旧 token 仍可验签

- **GIVEN** 旧键已退役并保留在 `retired/`
- **WHEN** 携旧键签发的 refresh 请求续期
- **THEN** 验签通过(JWKS 同时含新旧公钥),直至旧键签发 token 过期

#### Scenario: 弱/加密/缺失密钥拒绝启动

- **WHEN** 挂载目录缺少 `active.pem`、密钥不足 2048 位或为加密 PKCS#8
- **THEN** 启动失败并给出明确原因
