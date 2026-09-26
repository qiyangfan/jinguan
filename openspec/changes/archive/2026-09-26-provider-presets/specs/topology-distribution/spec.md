## ADDED Requirements

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
