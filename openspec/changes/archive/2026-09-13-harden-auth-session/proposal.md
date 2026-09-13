# 认证会话加固:双 token、防爆破、管理员重置密码

## Why

锦官是涉钱控制面,当前认证形态的三处短板:

1. **单 access token 12 小时不可吊销**:token 一旦泄露,攻击窗口 12 小时且无任何收回手段;对能操作资金的管理端,这是不可接受的风险敞口。12h 的初衷(免打扰)应由 refresh 机制承担,而非拉长 access 寿命。
2. **登录/注册无限流**:公网可达后可无限速爆破/撞库/批量注册(one-api 系均有防护)。
3. **无密码重置通道**:users 表连 email 都没有,用户忘密码只能进库改——ADMIN 重置是最小可行通道,邮件自服务随真实需求另排。
4. **登录用户名枚举(2026-09-12 复查补充)**:登录失败区分「用户不存在」(404)与「密码错误」(400),状态码与文案双差异,构成教科书级枚举向量(OWASP ASR 2.5.4)——统一「用户名或密码错误」。

被否决备选:①JWT 黑名单/登出吊销——需服务端存储,单实例规模下是过度设计,refresh 轮换已把泄露窗口从 12h 压到最短令牌寿命,否决;②access token 也进 httpOnly cookie(全 cookie 方案)——引入 CSRF 面,与 Bearer 无状态纪律冲突,否决;③验证码/Turnstile——依赖外部服务,per-IP 限流先行,不够再加,否决。

## What Changes

- **双 token**:access JWT TTL 降至 30 分钟,仅存前端内存(zustand,页面刷新即失);refresh JWT(7 天)经 `Set-Cookie` 下发(httpOnly + Secure + SameSite=Strict + Path=/auth),`POST /auth/refresh` 轮换签发;登出清 cookie。
- **前端会话链路**:应用挂载时静默 refresh 换取 access;401 时单飞(single-flight)刷新→重放原请求→失败清态回落登录(同时修复现状"注释声称 401 清态但实现不存在"的缺陷)。
- **登录防爆破**:per-IP 固定窗口限流(Caffeine,版本由 Boot BOM 管理,零新增三方风险),login 10 次/分钟、register 3 次/分钟,超限 429 ProblemDetail。
- **管理员重置密码**:`POST /admin/users/{id}/reset-password`(ADMIN),校验同注册密码约束,BCrypt 落库。

## Capabilities

- `auth-session`:REMOVED「无刷新与吊销机制(现状)」+ ADDED「双 token 会话」「登录防爆破」「管理员重置密码」

## Non-goals(防过度设计)

- 不做 refresh token 复用检测(需服务端存储轮换历史;单实例规模下登记为已知限制);
- 不做邮件验证/自助找回(users 表无 email,等门户需求一并立);
- 不做分布式限流存储(单实例,Redis 不引入;集群化时随 README 演进项一并处理);
- 不改 CORS/CSRF 模型:写请求全 Bearer 头,refresh cookie 仅用于 `/auth/*` 且 SameSite=Strict,无新增 CSRF 面。

## Impact

- 配置:`jinguan.security` 增 access/refresh TTL 两个键(原 jwt-ttl-minutes 由两个新键取代,迁移注释登记);
- 模块:security(签发/过滤/refresh 端点)、user(重置密码);跨模块仅经既有契约;
- 前端:api.ts(内存 token + 单飞刷新 + 挂载刷新)、auth-store(态调整)、LoginPage 不变;
- 契约:openapi.json 随 delivery-pipeline change 统一导出。
