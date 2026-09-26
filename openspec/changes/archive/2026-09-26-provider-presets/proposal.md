# 官方厂商预设目录(provider-presets)

## Why

拓扑三件套(供应商/路由/能力目录)录入至今只有裸 `PUT /admin/topology` 一条路:运营要对每个上游
厂商手抄 adapter 类型与 base_url——adapter 取值其实是 muniu 数据面的分类学知识
(`internal/adapter/adapter.go`:`openai/qwen/doubao/deepseek/moonshot/glm/anthropic` 七型),
base_url 拼接语义还各不相同(openai 基座要求含 `/v1` 等版本段、anthropic 桥接只吃裸域),
抄错一个字符故障就落在转发面且跨仓难定位。同类工具 cc-switch 的预设供应商体验证明了正确形态:
点一下官方厂商 chip,表单即回填。锦官作为控制面,应当把「muniu 能转什么、官方端点长什么样」
这份知识单源化成预设目录,并通过拓扑管理页暴露——只收官方端点(厂商第一方 API 域名),
不收中转站/代理商(质量与计费不可控,且与「账本+价目」的运营模型冲突)。

## What Changes

- 新增后端预设目录单源 `ProviderPresetCatalog`(常量,不落库):官方厂商
  (name 建议/adapter/base_url/env 引用名建议/建议模型/官网/区域/备注)。
- 新增 `GET /admin/topology/presets`(ADMIN,沿用 TopologyAdminController 的角色门):返回
  `{"presets":[ProviderPresetView]}` 包络;预设 adapter 必须 ⊆ muniu 支持集(测试钉死)。
- 新增前端「渠道拓扑」管理页(ADMIN):供应商增删改 + 官方预设一键回填(chip);
  model_routes / models 动态行编辑;`PUT /admin/topology` 整包保存,校验拒绝原样示人。
- openapi.json 重导出 + web `api-types.gen.ts` 同步(CI 契约漂移门禁要求)。

## 决策点(详见 design.md)

预设单源放后端常量 vs 前端静态 vs DB 表;预设是否含建议模型;
路由/能力目录的编辑形态(结构化动态行 vs 裸 JSON 文本域);anthropic 预设与桥接的备注口径。

## Impact

- 规格:`specs/topology-distribution/spec.md` ADDED「官方厂商预设目录」requirement。
- 前端:新页面 + 新 API 客户端 + 导航项;ui/ 标准件零改动(无双仓同步)。
- muniu 侧零开发:预设只是录入侧便利,拓扑下发契约(`TopologyPayload`)零变化。
- openapi.json 16→17 路径(确定性重导出)。
