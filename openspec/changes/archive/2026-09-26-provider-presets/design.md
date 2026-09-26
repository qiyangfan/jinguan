# 设计:官方厂商预设目录(provider-presets)

## D1 预设单源放哪:后端常量(裁决)/ 前端静态 / DB 表

- **后端常量(裁决)**:adapter 分类学是 muniu 数据面的知识(七型适配器),
  base_url 拼接语义随 adapter 而定——这份知识放 web 层 = 层间泄漏;放后端则
  openapi 契约化,任何管理客户端都能消费。muniu 演进适配器(加型/加 quirk)时,
  锦官预设同节奏演进,单源在侧天然对齐。
- **前端静态否决**:知识放错层;openapi 漂移门禁覆盖不到,双端各自维护必漂。
- **DB 表否决**:预设是低频演进的参考数据(厂商发新端点才变),不值得一张表 +
  CRUD + 迁移;且预设与代码中的校验联动(adapter ∈ 支持集)同源演进,落库反而
  制造「库里的预设引用了未实现 adapter」的裂缝。
- 附带收益:预设测试钉死 adapter ⊆ muniu 支持集、base_url 一律 https、name 唯一——
  目录本身被契约测试看住。

## D2 预设记录形态

`ProviderPresetView`(snake_case,与拓扑三件套同风格):

| 字段 | 说明 |
|---|---|
| `name` | 建议 provider 名(拓扑内唯一,如 `deepseek`;被占用时运营在表单改名,预设只是建议) |
| `display_name` | 中文展示名(`DeepSeek 官方`) |
| `adapter` | muniu 适配器类型(七型之一) |
| `base_url` | 官方端点精确形态:openai 系含版本段(转发时拼 `/chat/completions`、`/embeddings`);anthropic 为裸域(桥接拼 `/v1/messages`) |
| `env_suggestion` | 建议 env 引用名(`DEEPSEEK_API_KEY`);muniu 副本按此配环境变量 |
| `models` | 建议模型 ID(供能力目录/路由候选一键带入;2-4 个当期旗舰,运营可改) |
| `website` | 官网/控制台(申请 key 入口) |
| `region` | `cn` / `global`(前端分组展示) |
| `note` | 备注(双端点、兼容层形态、计费注意事项) |

models 是建议不是契约:拓扑的 models 能力目录本就是声明制,预设带入只是省打字。

## D3 端点形态

`GET /admin/topology/presets` → `{"presets":[...]}` 包络(与价目 `PricingEnvelope` 同风格)。
挂进 `TopologyAdminController`(类级 `@PreAuthorize("hasRole('ADMIN')")` 已覆盖,
`/admin/**` 在 SecurityConfig 亦要求 ADMIN——双门同源)。管理面只读端点,无写语义。

## D4 拓扑管理页:三件套编辑形态

- **providers(结构化卡片 + 预设 chip)**:本 change 的主菜。添加/编辑对话框顶部为
  预设区(按 region 分组 chip,点击回填 name/adapter/base_url/env_suggestion/
  models 暂存),下方表单可全部改写;keys 为动态行(env + weight,默认 1)。
  key 输入只收 env 引用名,绝不明文——与后端纪律同规,输入框 placeholder 明示。
- **model_routes(结构化动态行)**:alias 卡片列表,每卡候选行
  (provider 下拉[限已录供应商] + model + priority + weight);可加/删行、删 alias。
  删 provider 时若仍被 routes/models 引用,前端先软校验拦截提示(后端 400 兜底为权威)。
- **models 能力目录(结构化动态行)**:provider + model + drop_params(逗号分隔)+
  thinking_lock(checkbox)+ max_output_tokens;预设带入的模型先落这里(去重)。
- **保存 = `PUT /admin/topology` 整包**:回填即整包 GET,保存即整包 PUT;后端校验
  拒绝(空 routes/悬空引用/weight≤0)的中文 detail 原样示人。ETag 只读展示(保存后刷新)。

结构化动态行 vs 裸 JSON 文本域(被否决):三件套字段少且平坦,结构化编辑成本可控;
裸 JSON 把校验负担全推给运营,预设体验也无处安放。不做拖拽排序/批量导入等花活。

## D5 预设清单口径(只收官方,尽量多)

入选门槛:厂商第一方 API 域名 + muniu 七型适配器可转。约 17 家:
国内 DeepSeek / Kimi(moonshot,国内国际双端点)/ 智谱 GLM(bigmodel 与 z.ai 双端点)/
通义千问(DashScope 兼容模式,国内国际双端点)/ 火山方舟 Doubao / MiniMax /
硅基流动 / ModelScope / 腾讯混元 / 百度千帆 / 阶跃星辰;
国际 Anthropic(anthropic 桥接,裸域)/ OpenAI / xAI Grok / Gemini(官方 OpenAI 兼容层,
openai 适配器可转,备注注明)/ OpenRouter / Groq / Mistral。

- 中转站/代理商(PackyCode、ZetaAPI、各类 "Coding Plan" 二道贩子)一律不收:
  质量与计费不可控,与「账本+价目」运营模型冲突。
- 模型 ID 以立项时官方文档实证为准(研究记录随归档入 tasks 验收注记);
  模型迭代快,预设语义是「建议」,运营可改,不构成契约。
- Gemini 走 openai 兼容层而非独立 gemini 桥接:muniu 侧 gemini 桥接未实现(fail-fast),
  官方 OpenAI 兼容层经 openai 基座即可转,预设照收、备注注明。
