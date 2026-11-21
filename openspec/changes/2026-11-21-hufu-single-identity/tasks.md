# 任务:锦官去账号化 + 虎符 OIDC RP

> 资金/契约类先红后绿;每节以 `make check` 全绿收口。方向依据 hufu ADR-0010。

## 1. 失败测试与迁移

- [ ] 1.1 先写失败测试:无本地注册/登录/改密;principal subject 唯一;账本按 subject
- [ ] 1.2 Flyway 迁移:principals + ledger/orders/keys/refresh_sessions 外键改 subject
- [ ] 1.3 旧 `users` 与密码列删除;H2/PostgreSQL 双模式验证
- [ ] 1.4 make check 子集绿

## 2. JWT 与会话

- [ ] 2.1 `JwtService` 改 `sub`=subject,删除 `uid`;角色保留
- [ ] 2.2 `JwtAuthFilter`/钱包/支付/API key 按 subject
- [ ] 2.3 refresh 会话与轮换/重用检测按 subject
- [ ] 2.4 make check 子集绿

## 3. 删除本地认证

- [ ] 3.1 删除 register/login/password 端点与相关服务/测试
- [ ] 3.2 删除密码哈希与本地管理员账密路径
- [ ] 3.3 前端登录页只保留统一身份入口
- [ ] 3.4 make check 子集绿

## 4. OIDC RP

- [ ] 4.1 先写失败测试:state 单次、PKCE S256、nonce/iss/aud、JWKS 验签、超时
- [ ] 4.2 配置与发现文档/JWKS 缓存(离线验签)
- [ ] 4.3 `/auth/oidc/start|callback|enabled`;首登建无权限资料
- [ ] 4.4 回调签发本地双 token + HttpOnly refresh;前端成功页走 /auth/refresh
- [ ] 4.5 `/auth/logout/sso` 与本地登出联动
- [ ] 4.6 make check 子集绿

## 5. break-glass 与运维

- [ ] 5.1 密封凭据/CLI 铸造短时 ADMIN 会话;默认关闭、审计、可轮换
- [ ] 5.2 文档/runbook;hufu 不可用时的登录失败与已登录会话语义
- [ ] 5.3 make check 全绿

## 6. 契约与联调

- [ ] 6.1 更新 openapi.json/contracts;前端类型再生成
- [ ] 6.2 与虎符真实联调:授权码 + PKCE、首登授权、禁用、登出
- [ ] 6.3 回滚演练(旧库快照);验收记录
