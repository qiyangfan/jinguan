# 任务:粗角色改由 Keycloak client roles 提供

> 安全规则类改动先红后绿;每节以 `make check` 全绿收口。跨仓(platform)任务在对应仓完成。

## 1. 角色供给与失败测试

- [x] 1.1 platform:`setup-realm.sh` 建 client roles(`jinguan-web`: `jinguan-admin`/`jinguan-user`)
      并赋测试用户;幂等重跑验证(跨仓,platform 仓)
- [x] 1.2 实施前事实核查(30min):用真实 Keycloak 验证 id_token 是否携带
      `resource_access["jinguan-web"].roles`;若缺失,启用 roles client scope 的
      client-roles→ID token mapper,或改为从 access_token 读取;结论写回 design.md
- [x] 1.3 先写失败测试:映射(admin/user/并存→ADMIN)、未知角色丢弃、缺 claim→USER、
      realm role 忽略、租户/钱包 claim 忽略、角色撤销后重登收敛
- [x] 1.4 改写"首登恒 USER"相关既有断言

## 2. 实现

- [x] 2.1 回调解析 `resource_access["jinguan-web"].roles` 并按白名单映射
- [x] 2.2 `PrincipalService` 首登创建/登录同步角色;本地 `role` 降级为缓存
- [x] 2.3 退役 `POST /admin/users/{subject}/role`;保留 disable/enable
- [x] 2.4 前端移除角色授予操作(仅保留禁用/启用)
- [x] 2.5 `openspec validate --all --strict` 通过

## 3. 验收

- [x] 3.1 本地集成:USER 管理端点 403、ADMIN 200、角色撤销后重登降级
- [x] 3.2 Keycloak 运行时:登录/登出/JWKS/禁用/break-glass/账本归属按新 sub
- [x] 3.3 `make check` 全绿(含真 PG 用例与前端门禁)
      实测(2026-11-23,生成物入索引后):EXIT=0;后端 verify/真 PG、spec、license、
      前端 typecheck/lint/test/build/e2e/size、openapi 与 api-types 漂移检查全绿。

## 4. 文档

- [x] 4.1 `docs/runbook-single-identity.md`:角色授予改为 Keycloak 侧,删除本地角色端点说明
- [x] 4.2 README 身份段同步;longzhong `INTEGRATION-PLAN.md` §2 验收回写
- [x] 4.3 jinguan HANDOFF 更新(状态/验收记录)