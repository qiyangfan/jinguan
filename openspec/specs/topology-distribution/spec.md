# topology-distribution Specification

## Purpose
本 capability 定义渠道拓扑(供应商/模型路由/能力目录)的管理面运营与向数据面(muniu)的整包下发:管理端整包替换以事务校验保证引用完整与 key 不落明文,数据面经 `GET /control/v1/topology` 以 ETag 增量拉取并热更新池。

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

### Requirement: 官方厂商预设目录

系统必须 (MUST) 内置官方厂商预设目录并经 `GET /admin/topology/presets` 暴露(ADMIN 角色,
响应形态 `{"presets":[ProviderPresetView]}`,snake_case)。每条预设必须 (MUST) 含
`name/display_name/adapter/base_url/env_suggestion/models/website/region/note`,且:
`adapter` 必须 (MUST) ∈ muniu 适配器支持集(openai/qwen/doubao/deepseek/moonshot/glm/
anthropic);`base_url` 必须 (MUST) 为厂商第一方 API 域名且 https;目录内 `name` 必须 (MUST)
唯一。预设目录必须 (MUST) 不收中转站/代理商端点。预设是录入建议而非运营态:
修改预设随代码发布,不提供运行时增删改。

#### Scenario: 预设适配器可控

- **WHEN** 管理端拉取预设目录
- **THEN** 每条预设的 adapter 均落在 muniu 七型支持集内,经表单回填后 `PUT /admin/topology`
  必然通过数据面适配器装配,不存在「预设引导出数据面不认识的 adapter」

#### Scenario: 非管理员不可见

- **WHEN** USER 角色 GET /admin/topology/presets
- **THEN** 403,与管理面其余端点同规

### Requirement: 拓扑管理页

前端必须 (MUST) 提供「渠道拓扑」管理页(ADMIN):供应商增删改 + 预设一键回填
(回填 name/adapter/base_url/env 建议/建议模型)、model_routes 与 models 结构化编辑、
`PUT /admin/topology` 整包保存;key 一律 (MUST) 只收 env 引用名,输入面绝不明文;
后端校验拒绝的 detail 必须 (MUST) 原样示人。

#### Scenario: 预设一键回填

- **WHEN** 运营在添加供应商对话框点击某官方厂商 chip
- **THEN** 表单回填该预设的 name/adapter/base_url 与 env 引用名建议,运营补入 weight
  后保存,整包 PUT 成功且拓扑下发即含新供应商

#### Scenario: 悬空引用软校验

- **WHEN** 运营删除仍被 model_routes 或 models 引用的供应商
- **THEN** 页面先行拦截并提示先清理引用(后端悬空引用 400 为权威兜底)
