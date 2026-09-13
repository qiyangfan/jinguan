# topology-distribution Specification

## Purpose
TBD - created by archiving change publish-topology. Update Purpose after archive.

## Requirements

### Requirement: 拓扑运营态(整包替换)

ADMIN 必须能 (MUST) 经 `PUT /admin/topology` 整包替换拓扑三件套(providers/model_routes/models),服务层在单事务内校验通过后 delete-all + insert-all;校验必须 (MUST) 拒绝:model_routes 为空、routes/models 引用不存在于本次提交 providers 的悬空引用、provider weight ≤ 0、JSON 列形状非法——拒绝时旧拓扑必须 (MUST) 原封不动。上游 key 只允许 (MUST) 以 env 引用名存储,绝不明文。

#### Scenario: 悬空引用拒绝

- **WHEN** 提交 routes 引用 providers 中不存在的 provider "ghost"
- **THEN** 400,拓扑保持原值;修正后重放成功且下次下发即含新拓扑

#### Scenario: 相同内容重放幂等

- **WHEN** 以与当前一致的三件套重复 PUT
- **THEN** 替换成功且下发 ETag 不变(muniu 拉取循环自然 304 短路)

### Requirement: 下发契约端点

`GET /control/v1/topology` 必须 (MUST) 返回与 muniu TopologyPayload 逐字段同构的三件套 JSON(providers[name/adapter/base_url/keys[env,weight]/weight/priority]、model_routes[alias→provider/model/priority/weight]、models[provider/model/drop_params/thinking_lock/max_output_tokens],snake_case);响应必须 (MUST) 携带内容哈希 ETag(双引号包裹),请求带命中 If-None-Match 时必须 (MUST) 返回 304 空体。

#### Scenario: 逐字段契约

- **WHEN** muniu 以 topology_url 拉取并经 ToTopology 装配
- **THEN** 响应被数据面直接接受并完成热替换,无字段漂移

#### Scenario: 条件请求短路

- **WHEN** muniu 携带上次 ETag 再拉取且拓扑未变
- **THEN** 304 空体,muniu 保持现快照零动作

### Requirement: 拉取端点无认证

`GET /control/v1/topology` 必须 (MUST) 匿名放行,与价目/吊销下发端点同款依赖部署网络隔离保护(现实契约);muniu 侧增加拉取认证头为双方演进项。

#### Scenario: 现状形态

- **WHEN** 任意来源 GET /control/v1/topology
- **THEN** 返回 200 与当前三件套(未录入时为空三件套,不 404)

### Requirement: 空拓扑下发语义

未录入拓扑时端点必须 (MUST) 返回 200 空三件套(`providers:[]`、`model_routes:{}`、`models:[]`);muniu 侧应用校验拒绝空 routes 并保持文件源降级——「cluster 形态上线前先录入拓扑」为运维前置条件。

#### Scenario: 未录入空态

- **WHEN** 全新部署锦官后 muniu 直接拉取
- **THEN** 200 空三件套;muniu 拒绝应用保持文件源并告警,不发垃圾流量
