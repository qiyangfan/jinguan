# 任务清单:认证会话加固

> 纪律:安全/契约类改动先红后绿;收口 `make check` 全绿。

## 1. 双 token 后端

- [ ] 1.1 JwtService 扩展:access(30min)/refresh(7d)双 TTL 与 typ claim;配置键 `jwt-access-ttl-minutes`/`jwt-refresh-ttl-days` 取代 `jwt-ttl-minutes`(application.yml 迁移注释)
- [ ] 1.2 `POST /auth/refresh`:读 refresh cookie → 验签 → 轮换签发新 access + 新 refresh(Set-Cookie 属性齐全);`POST /auth/logout` 清 cookie;测试:过期/篡改/缺 cookie 各负面
- [ ] 1.3 登录响应同步双发(access 体内 + refresh cookie);JwtAuthFilter 语义不变(只认 access)

## 2. 前端会话链路

- [ ] 2.1 api.ts:access 移入内存(auth-store),localStorage 清空 token;fetch 包装器 401 → 单飞刷新 → 重放 → 失败清态
- [ ] 2.2 App 挂载静默 refresh(恢复会话);登出调 logout 端点 + 清内存;RequireAdmin/路由守卫回归
- [ ] 2.3 刷新页面不掉登录实证;并发请求 401 只触发一次 refresh 的测试

## 3. 防爆破与重置

- [ ] 3.1 Caffeine per-IP 固定窗口限流(login 10/min,register 3/min,429 ProblemDetail);测试:超限 429、窗口滑过恢复、X-Forwarded-For 开关语义
- [ ] 3.2 `POST /admin/users/{id}/reset-password`(ADMIN,密码约束同注册);测试:USER 调用 403、弱密码 400、重置后旧密码失效新密码可登录
- [ ] 3.3 登录失败提示统一「用户名或密码错误」(现状区分「用户不存在」404/「密码错误」400,构成用户名枚举向量);测试钉死两形态同状态码同文案

## 4. 收口

- [ ] 4.1 DemoLoopIntegrationTest 认证链路回归;`make check` 全绿
