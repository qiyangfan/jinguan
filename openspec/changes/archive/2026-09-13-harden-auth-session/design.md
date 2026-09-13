# 设计:双 token 会话

## D1 令牌分工与参数

| 令牌 | TTL | 载体 | 用途 |
|---|---|---|---|
| access | 30 分钟 | 响应体 JSON → 前端内存(zustand) | 全部 API 的 Bearer 认证(现状不变) |
| refresh | 7 天 | `Set-Cookie`(httpOnly + Secure + SameSite=Strict + Path=/auth) | 仅 `POST /auth/refresh` 与登出 |

选型理由:access 存内存使 XSS 即便得手也只能偷到 ≤30 分钟寿命的令牌,页面刷新静默续期无感知损失;refresh 进 httpOnly cookie 使 JS 完全读不到长效凭证。SameSite=Strict 下浏览器跨站不携带该 cookie,`/auth/refresh` 无需 CSRF token;其余 API 走 Bearer 头,本无 cookie 可附,CSRF 面不变。Secure 在本地 dev 放行 http(按 profile 配置)。

## D2 轮换与已知限制

每次 refresh 成功签发**新 refresh token**(轮换),旧 refresh 立即失效性依赖其短寿命与新签发——**不做复用检测**(需服务端存轮换链,单实例规模过度设计)。已知限制:refresh token 在 7 天内无法服务端吊销;缓解:Path 限定 /auth + httpOnly + 登出即清;若出现"ADMIN 账号疑似失陷需立即全端踢出"的真实需求,再立全局会话版本号(`users.session_epoch`,签发时入 claim,校验时比对——成本两列一个 filter 检查,登记触发条件,现在不做)。

## D3 前端会话链路

- 挂载时(有 refresh cookie 语义上不可知,直接尝试)调 `/auth/refresh`:成功→ access 入内存并解码 uid/role;失败→ guest 态。消除"刷新页面掉登录"与 localStorage 持久化的 XSS 敞口,localStorage 中不再存任何 token。
- 401 拦截:api.ts fetch 包装器捕获 401 → 单飞刷新(并发请求只触发一次 refresh,其余挂起等待)→ 成功则重放原请求 → 失败清态回落登录。与现状"注释声称、实现不存在"的缺陷一并闭环。

## D4 限流

per-IP 固定窗口,Caffeine `ExpiringTicker` 不需要——固定窗口用 `Caffeine cache(ip → AtomicLong)`,expireAfterAccess(窗口长)+ maximumSize(10 万,防内存打爆)。login 10/min、register 3/min,超限 429 + ProblemDetail(`type` 归一,对齐 GlobalExceptionHandler 风格)。取 IP 经 `X-Forwarded-For` 首值仅当部署在可信反代后(配置开关,默认取 remoteAddr)。登记:集群化后需换分布式限流(README 演进项已有集群条目)。
