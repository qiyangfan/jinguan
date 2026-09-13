# 拓扑下发:第四根管子(锦官 → muniu)

## Why

muniu 侧(Aiator-0005 配置源抽象 3.2)已就绪拓扑拉取管道:`control_plane.topology_url` 配置字段、
ETag 条件请求、损坏拒绝应用、失联保持降级全部实现并有测试钉住,但控制面(锦官)一直没有供给端点
——cluster 参考部署(deploy/cluster/)只能以挂载文件静态拓扑兜底。补齐本端点后:
①cluster 副本拓扑经锦官集中运营,改拓扑即热替换生效(数据面零重启);
②与既有三根管子(价目/吊销/ingest)对齐,锦官成为完整的 cluster 控制面。

## What Changes

- 新增 `GET /control/v1/topology` 下发契约端点(无认证 GET,沿用网络隔离契约;ETag 条件请求,304 短路)。
- 新增 `/admin/topology` 管理端点:GET 读当前三件套 + PUT 整包替换(事务,校验后 delete+insert)。
- 新增迁移 V7 三表:`topology_providers` / `topology_routes` / `topology_models`。
- SecurityConfig 放行 `GET /control/v1/topology`(与 /control/v1/pricing 同款注记)。

## 决策点(详见 design.md)

存储三表规范化 vs 单 JSON 文档;管理形态整包 PUT vs 分表 CRUD;ETag=内容哈希;
空拓扑下发语义;key 只存 env 引用名绝不明文。

## Impact

- 新 capability:`specs/topology-distribution/spec.md`(ADDED)。
- muniu 侧**零开发**(拉取管道现成);deploy/cluster 工件的 topology_url 注释在联调后启用。
- openapi.json 重导出 + web `api-types.gen.ts` 同步(CI 契约漂移门禁要求)。
