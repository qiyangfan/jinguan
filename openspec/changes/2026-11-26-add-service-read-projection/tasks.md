# Tasks

## 1. 投影与缓存

- [ ] 1.1 投影 envelope/缓存模型（source/observed_at/stale/ETag）。验证：正常/无投影/stale。
- [ ] 1.2 服务只读 API 客户端与服务身份认证。验证：未认证 401/403。
- [ ] 1.3 字段白名单/脱敏。验证：凭据/token/密钥不返回、不缓存。
- [ ] 1.4 静态地址映射。验证：service_id→base_url 来自部署配置；缺失显示未知；不读注册表 endpoint。
- [ ] 1.5 投影保留/清理。验证：过期清理/归档审计；服务恢复重新拉取；无敏感残留。

## 2. 边界与降级

- [ ] 2.1 禁止直连库/跨库 JOIN/CDC。验证：实现/配置/测试无服务 DB 连接。
- [ ] 2.2 停服务/停锦官 smoke。验证：服务 last-known；锦官共享控制可用、服务页 stale。
- [ ] 2.3 服务 schema 演进验证。验证：只读 API 契约不变时锦官无需迁移。

## 3. 验收与独立运行

- [ ] 3.1 权限负向与跨租户用例。验证：未授权 401/403，跨租户 404/不可见。
- [ ] 3.2 审计断言。验证：普通列表不写审计；敏感详情/导出写审计；失败/拒绝可追责；无字段值。
- [ ] 3.3 契约与生成客户端。验证：OpenAPI/生成物无漂移；跨仓契约测试必跑（X-01）。
- [ ] 3.4 Greenfield 重置：删除/禁用旧直连读路径。验证：无兼容/双轨，旧连接/旧端点不存在。

## Workflow follow-up

- 归档前跑 `openspec validate --strict`；实现完成后按仓库门禁收口。
- 本 change 与 longzhong 聚合层 `docs/CONTROL-PLANE-PROJECT.md` 及 `docs/CONTROL-PLANE-BOUNDARY.md` 的对应 WP/边界表保持一致。
