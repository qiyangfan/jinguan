# 任务清单:拓扑下发(publish-topology)

> 纪律:契约/校验改动测试先行(先红后绿);每任务收尾 = 实现 + 测试 + `make check` 全绿 + 勾选。

## 1. 存储与领域

- [x] 1.1 迁移 V7:`topology_providers` / `topology_routes` / `topology_models` 三表(H2+MySQL 兼容列型,JSON 列 TEXT)
- [x] 1.2 实体 ×3 + Spring Data 仓库 ×3(JpaSpecificationExecutor 不需要,基础 CRUD)
- [x] 1.3 `TopologyService`:下发视图(records:provider/candidate/capability,snake_case 序列化)+ 当前 ETag(内容哈希)+ `replaceAll` 整包校验与事务替换

## 2. 契约端点

- [x] 2.1 `TopologyIngressController`:`GET /control/v1/topology`(200 + ETag;If-None-Match 命中 304;空态 200 空三件套)
- [x] 2.2 SecurityConfig 放行 `GET /control/v1/topology`(与 pricing/key-revocations 同款网络隔离注记)
- [x] 2.3 `TopologyAdminController`:`GET/PUT /admin/topology`(ADMIN;PUT 整包校验:routes 非空/引用完整/weight>0/JSON 形状)

## 3. 测试(先红后绿)

- [x] 3.1 契约测试:下发负载逐字段对齐 muniu TopologyPayload 接收形态(providers/model_routes/models 三件套,keys 含 env+weight)
- [x] 3.2 条件请求测试:首次 200+ETag → 带 If-None-Match 再拉 304 → PUT 内容变化后 ETag 更新 → PUT 相同内容重放 ETag 不变
- [x] 3.3 校验拒绝测试:空 routes 400 / 悬空 provider 引用 400 / provider weight≤0 400 / 拒绝后旧拓扑原封不动
- [x] 3.4 空态测试:未录入时下发 200 空三件套

## 4. 收尾

- [x] 4.1 openapi.json 重导出(确定性双跑 diff 空)+ web `api-types.gen.ts` 同步(contracts:gen)
- [x] 4.2 `make check` 全绿(后端测试含三静态门禁 + 前端门禁)
- [x] 4.3 muniu 联调冒烟实录:起锦官录入拓扑 → muniu 配 topology_url 拉取成功 → PUT 变更热替换 → 锦官失联 muniu 保持旧快照 → ETag 304 短路
- [x] 4.4 deploy/cluster 工件启用 topology_url + README 契约表更新(联调实录入表);归档本 change
