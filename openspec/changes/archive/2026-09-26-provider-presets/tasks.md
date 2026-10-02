# 任务清单:官方厂商预设目录(provider-presets)

> 纪律:契约类改动测试先行(先红后绿);每任务收尾 = 实现 + 测试 + `make check` 全绿 + 勾选。

## 1. 后端预设目录

- [x] 1.1 `ProviderPresetCatalog`(常量单源):29 家官方厂商预设(国内 16 + 国际 13,含国内厂
      Anthropic 兼容分支),只收厂商第一方 API 域名
- [x] 1.2 `GET /admin/topology/presets`(挂 TopologyAdminController):返回
      `{"presets":[ProviderPresetView]}` 包络,snake_case 与拓扑三件套同风格

## 2. 测试(先红后绿)

- [x] 2.1 目录自检测试(`ProviderPresetCatalogTest`):预设 adapter ⊆ muniu 七型支持集
      (openai/qwen/doubao/deepseek/moonshot/glm/anthropic)、base_url 一律 https 无尾斜杠、
      name 唯一、env_suggestion 全大写下划线以 `_API_KEY` 收尾、models/website/region/note 齐备
- [x] 2.2 契约测试(`TopologyPresetsIntegrationTest`):ADMIN 拉 presets 200 包络且九字段
      逐一在位(schema 断言);USER 403;无凭证 403(无 entry-point 默认拒绝形态,与 /admin 同规)

## 3. 前端拓扑管理页

- [x] 3.1 `lib/topology-api.ts`:GET/PUT `/admin/topology` + GET presets 客户端
      (api 对象新增 `putEtag` 取保存返回的 ETag;类型对齐 api-types.gen 并收窄必需字段)
- [x] 3.2 `pages/TopologyPage.tsx`(ADMIN,导航「渠道拓扑」):供应商卡片列表 +
      添加/编辑对话框(预设 chip 区按 cn/global 分组,一键回填 name/adapter/base_url/env 建议;
      keys 动态行只收 env 引用名,placeholder 明示绝不粘贴真实密钥)
- [x] 3.3 model_routes / models 动态行编辑(provider 下拉限已录供应商、删供应商软校验引用、
      AlertDialog 确认)、`PUT` 整包保存、软校验 + 后端校验 detail 原样示人、ETag 只读展示、
      预设建议模型保存时自动写入能力目录(去重)
- [x] 3.4 路由挂载(/topology,RequireAdmin)+ AppShell 导航项(Network 图标);
      前端五门禁(typecheck/lint/stylelint/prettier/vitest)全绿;亮暗两态与三档宽度走查见验收注记

## 4. 收尾

- [x] 4.1 openapi.json 重导出(16→17 管理路径,springdoc 直出紧凑单行零格式漂移)+
      web `api-types.gen.ts` 同步(contracts:gen,+50 行 Preset/PresetEnvelope)
- [x] 4.2 `make check` 全绿(后端 54 测试含三静态门禁 + 前端五门禁)
- [x] 4.3 预设清单逐家核对官方文档实证记录(研究结论回写本文件验收注记);README 渠道段同步
- [x] 4.4 审查迭代循环通过;归档本 change
      (验收注记:评审迭代循环单遍收敛见 HANDOFF 验收记录 6「3 处长文本触屏可达性」;change 已归档本目录——勾选欠账 2026-10-02 补记)

## 验收注记(4.3 研究结论回写,2026-09-26)

**实证等级**:【实证】= 官方文档原文可见;【半实证】= 官方页确认存在,精确串来自搜索摘要/多源
一致;【推测】= 高把握但官方原文未直抓。模型 ID 迭代快,预设语义是「建议」,运营可改。

- **OpenAI 兼容分支**:DeepSeek `https://api.deepseek.com`(不带 /v1)【实证】;
  Kimi 国内 `api.moonshot.cn/v1` / 国际 `api.moonshot.ai/v1`【实证】;GLM `open.bigmodel.cn/api/paas/v4`
  【实证】;Qwen `dashscope.aliyuncs.com/compatible-mode/v1`【推测·高把握】;MiniMax 国内
  `api.minimaxi.com/v1` / 国际 `api.minimax.io/v1`【半实证】;SiliconFlow【实证】;其余
  (Doubao/ModelScope/混元/千帆/阶跃/OpenAI/xAI/Gemini/OpenRouter/Groq/Mistral)见目录 note。
- **Anthropic 兼容分支(用户点名收录)**:DeepSeek `api.deepseek.com/anthropic`【实证,官方文档
  原文,含 claude-* 模型名自动映射】;Kimi 国内/国际 `/anthropic`【实证】;GLM `/api/anthropic`
  与 z.ai `/api/anthropic`【半实证】;**Qwen 路径是 `/apps/anthropic` 非 `/api/anthropic`**【实证,
  阿里云 Model Studio 官方 Claude Code 指南】;MiniMax 国际 `/anthropic`【半实证】、国内【推测】;
  火山方舟/腾讯混元确认无官方分支【实证(负面)】。
- **模型 ID 代际(2026-09 实证)**:DeepSeek V4 代(deepseek-v4-pro/flash,K2 旧名 deepseek-chat/
  reasoner 已下线);Kimi K3 旗舰(kimi-k2-0905-preview 等 2026-05 已下线);GLM 5.3
  (glm-5.3/glm-5.3-flash,quick-start 实证);Qwen 3.7 代(qwen3.7-max/qwen3.7-plus);OpenAI
  GPT-5.2 代【实证】。api.kimi.com(Kimi For Coding)在官方 API 文档零出现,不入库。
- **已知未实证项(带假设立库,低风险)**:z.ai 国际两串 URL、混元/千帆/阶跃 base_url、ModelScope
  模型 ID。均为目录 note 已注明「以官方文档为准」语义;若失准,修 `ProviderPresetCatalog` 一处即可。
