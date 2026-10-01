package com.longzhong.jinguan.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 官方厂商预设目录(录入侧单源,常量不落库):把 muniu 数据面的适配器分类学与各厂商官方端点 钉在一处,经 {@code GET /admin/topology/presets}
 * 供拓扑管理页一键回填。只收厂商第一方 API 域名,不收中转站/代理商;聚合类平台仅收其官方端点且在 note 自明身份 (如 OpenRouter)。adapter 取值必须 ⊆ muniu
 * {@code internal/adapter/adapter.go} For()
 * 工厂支持集(openai/qwen/doubao/deepseek/moonshot/glm/anthropic),越界即数据面 fail-fast。
 *
 * <p>base_url 拼接语义:openai 系须含版本段(转发拼 /chat/completions、/embeddings), anthropic 系为协议分支基址(muniu 桥接拼
 * /v1/messages)——各厂 Anthropic 分支路径形态 不统一(/anthropic、/api/anthropic、/apps/anthropic 并存),base_url
 * 必须整串可配。 模型 ID 以 2026-09-26 官方文档核验为准,随厂商迭代会过时,预设语义是「建议」非契约。
 *
 * <p>env 建议名全库统一 _API_KEY 收尾:这是锦官/muniu 的内部 env 引用名(非厂商 SDK 约定, 与各家 ANTHROPIC_AUTH_TOKEN
 * 之类惯例无关),统一形态降低运营心智负担;同厂商双分支 (OpenAI 兼容 / Anthropic 兼容)用不同 env 名,允许两分支独立配钥匙与轮换。
 */
public final class ProviderPresetCatalog {

  /** muniu 适配器类型(与 TopologyPayload adapter 字段同词汇)。 */
  static final String ADAPTER_OPENAI = "openai";

  static final String ADAPTER_QWEN = "qwen";
  static final String ADAPTER_DOUBAO = "doubao";
  static final String ADAPTER_DEEPSEEK = "deepseek";
  static final String ADAPTER_MOONSHOT = "moonshot";
  static final String ADAPTER_GLM = "glm";
  static final String ADAPTER_ANTHROPIC = "anthropic";

  /** 区域分组(前端分组展示用)。 */
  static final String REGION_CN = "cn";

  static final String REGION_GLOBAL = "global";

  /**
   * 单条官方厂商预设(snake_case 线上形态,与拓扑三件套同风格)。
   *
   * @param name 建议 provider 名(拓扑内唯一;被占用时运营在表单改名,预设只是建议)
   * @param displayName 中文展示名
   * @param adapter muniu 适配器类型
   * @param baseUrl 官方端点精确形态
   * @param envSuggestion 建议 env 引用名(muniu 副本按此配环境变量,绝不明文 key)
   * @param models 建议模型 ID(能力目录/路由候选一键带入)
   * @param website 官网/控制台(申请 key 入口)
   * @param region cn / global
   * @param note 备注(分支语义、模型映射、计费注意等)
   */
  public record Preset(
      @JsonProperty("name") String name,
      @JsonProperty("display_name") String displayName,
      @JsonProperty("adapter") String adapter,
      @JsonProperty("base_url") String baseUrl,
      @JsonProperty("env_suggestion") String envSuggestion,
      @JsonProperty("models") List<String> models,
      @JsonProperty("website") String website,
      @JsonProperty("region") String region,
      @JsonProperty("note") String note) {}

  /** 包络(与价目 PricingEnvelope 同风格,留出未来扩展位)。 */
  public record PresetEnvelope(@JsonProperty("presets") List<Preset> presets) {}

  private static final List<Preset> PRESETS =
      List.of(
          // ── 国内官方 · OpenAI 兼容分支 ──
          new Preset(
              "deepseek",
              "DeepSeek 官方",
              ADAPTER_DEEPSEEK,
              "https://api.deepseek.com",
              "DEEPSEEK_API_KEY",
              List.of("deepseek-v4-pro", "deepseek-flash"),
              "https://platform.deepseek.com",
              REGION_CN,
              "OpenAI 兼容(base_url 不带 /v1);当期旗舰 V4 代;峰谷时段差价计费;"
                  + "缓存命中与 402 欠费语义由 muniu deepseek quirk 归一"),
          new Preset(
              "kimi",
              "Kimi(月之暗面)官方",
              ADAPTER_MOONSHOT,
              "https://api.moonshot.cn/v1",
              "MOONSHOT_API_KEY",
              List.of("kimi-k3", "kimi-k2.7-code", "kimi-k2.7-code-highspeed"),
              "https://platform.kimi.com",
              REGION_CN,
              "国内站;当期旗舰 K3,K2 系旧快照与 moonshot-v1 系已下线;"
                  + "流式 usage 需 include_usage(muniu moonshot quirk 已声明)"),
          new Preset(
              "glm",
              "智谱 GLM 官方",
              ADAPTER_GLM,
              "https://open.bigmodel.cn/api/paas/v4",
              "GLM_API_KEY",
              List.of("glm-5.3", "glm-5.3-flash"),
              "https://open.bigmodel.cn",
              REGION_CN,
              "国内主站(开放平台);国际站见 glm-global(z.ai)"),
          new Preset(
              "qwen",
              "通义千问(百炼)官方",
              ADAPTER_QWEN,
              "https://dashscope.aliyuncs.com/compatible-mode/v1",
              "DASHSCOPE_API_KEY",
              List.of("qwen3.7-max", "qwen3.7-plus"),
              "https://bailian.console.aliyun.com",
              REGION_CN,
              "阿里云百炼 OpenAI 兼容模式;国际站见 qwen-global;Anthropic 分支见 qwen-anthropic"),
          new Preset(
              "doubao",
              "火山方舟(豆包)官方",
              ADAPTER_DOUBAO,
              "https://ark.cn-beijing.volces.com/api/v3",
              "ARK_API_KEY",
              List.of("doubao-seed-1-6-250615"),
              "https://console.volcengine.com/ark",
              REGION_CN,
              "模型名直连或 ep-xxx 推理接入点,以方舟控制台为准;无官方 Anthropic 分支"),
          new Preset(
              "minimax",
              "MiniMax 官方",
              ADAPTER_OPENAI,
              "https://api.minimaxi.com/v1",
              "MINIMAX_API_KEY",
              List.of("MiniMax-M2"),
              "https://platform.minimaxi.com",
              REGION_CN,
              "国内站(域名多一个 i);仅 chat/completions 无 Responses API;与国际站 Key 不互通"),
          new Preset(
              "siliconflow",
              "硅基流动 SiliconFlow",
              ADAPTER_OPENAI,
              "https://api.siliconflow.cn/v1",
              "SILICONFLOW_API_KEY",
              List.of("deepseek-ai/DeepSeek-V4-Flash", "moonshotai/Kimi-K2.7-Code"),
              "https://cloud.siliconflow.cn",
              REGION_CN,
              "开源模型官方托管云,模型 ID 带 org/ 前缀;国际站 api.siliconflow.us/v1"),
          new Preset(
              "modelscope",
              "ModelScope 魔搭官方推理",
              ADAPTER_OPENAI,
              "https://api-inference.modelscope.cn/v1",
              "MODELSCOPE_API_KEY",
              List.of("deepseek-ai/DeepSeek-V3.2", "ZhipuAI/GLM-4.6"),
              "https://modelscope.cn",
              REGION_CN,
              "官方 API-Inference(免费额度限流),模型 ID 带 org/ 前缀,以魔搭模型页为准"),
          new Preset(
              "hunyuan",
              "腾讯混元官方",
              ADAPTER_OPENAI,
              "https://api.hunyuan.cloud.tencent.com/v1",
              "HUNYUAN_API_KEY",
              List.of("hunyuan-turbos-latest", "hunyuan-t1-latest"),
              "https://console.cloud.tencent.com/hunyuan",
              REGION_CN,
              "OpenAI 兼容端点"),
          new Preset(
              "qianfan",
              "百度千帆官方",
              ADAPTER_OPENAI,
              "https://qianfan.baidubce.com/v2",
              "QIANFAN_API_KEY",
              List.of("ernie-4.5-turbo-128k"),
              "https://console.bce.baidu.com/qianfan",
              REGION_CN,
              "v2 OpenAI 兼容端点"),
          new Preset(
              "stepfun",
              "阶跃星辰官方",
              ADAPTER_OPENAI,
              "https://api.stepfun.com/v1",
              "STEPFUN_API_KEY",
              List.of("step-3", "step-2-16k"),
              "https://platform.stepfun.com",
              REGION_CN,
              "OpenAI 兼容;另有 Messages API(形态见官方文档)"),
          // ── 国内官方 · Anthropic 兼容分支(Claude Code 生态直连)──
          new Preset(
              "deepseek-anthropic",
              "DeepSeek 官方 · Anthropic 分支",
              ADAPTER_ANTHROPIC,
              "https://api.deepseek.com/anthropic",
              "DEEPSEEK_ANTHROPIC_API_KEY",
              List.of("deepseek-v4-pro", "deepseek-flash"),
              "https://platform.deepseek.com",
              REGION_CN,
              "Anthropic 协议分支:claude-opus* 自动映射 deepseek-v4-pro,其余 claude-* 映射"
                  + " deepseek-flash(官方内置;经网关时路由 model 亦可直接写 deepseek 名)"),
          new Preset(
              "kimi-anthropic",
              "Kimi 官方 · Anthropic 分支",
              ADAPTER_ANTHROPIC,
              "https://api.moonshot.cn/anthropic",
              "MOONSHOT_ANTHROPIC_API_KEY",
              List.of("kimi-k3", "kimi-k2.7-code"),
              "https://platform.kimi.com",
              REGION_CN,
              "Anthropic 协议分支(Claude Code 生态)"),
          new Preset(
              "glm-anthropic",
              "智谱 GLM 官方 · Anthropic 分支",
              ADAPTER_ANTHROPIC,
              "https://open.bigmodel.cn/api/anthropic",
              "GLM_ANTHROPIC_API_KEY",
              List.of("glm-5.3", "glm-5.3-flash"),
              "https://open.bigmodel.cn",
              REGION_CN,
              "Anthropic 协议分支(路径 /api/anthropic)"),
          new Preset(
              "qwen-anthropic",
              "通义千问官方 · Anthropic 分支",
              ADAPTER_ANTHROPIC,
              "https://dashscope.aliyuncs.com/apps/anthropic",
              "DASHSCOPE_ANTHROPIC_API_KEY",
              List.of("qwen3.7-max", "qwen3.7-plus", "qwen3.6-flash"),
              "https://bailian.console.aliyun.com",
              REGION_CN,
              "Anthropic 协议分支(路径 /apps/anthropic,注意非 /api/anthropic);"
                  + "官方按 haiku/sonnet/opus 角色绑不同模型,经网关时路由 model 即上游真名"),
          new Preset(
              "minimax-anthropic",
              "MiniMax 官方 · Anthropic 分支",
              ADAPTER_ANTHROPIC,
              "https://api.minimaxi.com/anthropic",
              "MINIMAX_ANTHROPIC_API_KEY",
              List.of("MiniMax-M2"),
              "https://platform.minimaxi.com",
              REGION_CN,
              "Anthropic 协议分支;国际站为 api.minimax.io/anthropic"),
          // ── 国际官方 ──
          new Preset(
              "kimi-global",
              "Kimi 国际版",
              ADAPTER_MOONSHOT,
              "https://api.moonshot.ai/v1",
              "MOONSHOT_GLOBAL_API_KEY",
              List.of("kimi-k3", "kimi-k2.7-code"),
              "https://platform.kimi.ai",
              REGION_GLOBAL,
              "国际站端点,与国内站账号体系独立"),
          new Preset(
              "kimi-global-anthropic",
              "Kimi 国际版 · Anthropic 分支",
              ADAPTER_ANTHROPIC,
              "https://api.moonshot.ai/anthropic",
              "MOONSHOT_GLOBAL_ANTHROPIC_API_KEY",
              List.of("kimi-k3", "kimi-k2.7-code"),
              "https://platform.kimi.ai",
              REGION_GLOBAL,
              "国际站 Anthropic 协议分支(官方明示 Claude Code 可用)"),
          new Preset(
              "glm-global",
              "智谱 GLM 国际版(z.ai)",
              ADAPTER_GLM,
              "https://api.z.ai/api/paas/v4",
              "ZAI_API_KEY",
              List.of("glm-5.3", "glm-5.3-flash"),
              "https://z.ai",
              REGION_GLOBAL,
              "国际站(z.ai 开放平台),与国内站账号体系独立"),
          new Preset(
              "glm-global-anthropic",
              "智谱 GLM 国际版 · Anthropic 分支",
              ADAPTER_ANTHROPIC,
              "https://api.z.ai/api/anthropic",
              "ZAI_ANTHROPIC_API_KEY",
              List.of("glm-5.3", "glm-5.3-flash"),
              "https://docs.z.ai",
              REGION_GLOBAL,
              "国际站 Anthropic 协议分支(GLM Coding Plan)"),
          new Preset(
              "qwen-global",
              "通义千问国际版(Model Studio)",
              ADAPTER_QWEN,
              "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
              "DASHSCOPE_GLOBAL_API_KEY",
              List.of("qwen3.7-max", "qwen3.7-plus"),
              "https://www.alibabacloud.com/en/product/modelstudio",
              REGION_GLOBAL,
              "国际站端点,与国内站账号体系独立"),
          new Preset(
              "minimax-global",
              "MiniMax 国际版",
              ADAPTER_OPENAI,
              "https://api.minimax.io/v1",
              "MINIMAX_GLOBAL_API_KEY",
              List.of("MiniMax-M2"),
              "https://www.minimax.io",
              REGION_GLOBAL,
              "国际站端点;与国内站 Key 不互通"),
          new Preset(
              "anthropic",
              "Claude 官方(Anthropic)",
              ADAPTER_ANTHROPIC,
              "https://api.anthropic.com",
              "ANTHROPIC_API_KEY",
              List.of("claude-sonnet-4-5", "claude-opus-4-1", "claude-haiku-4-5"),
              "https://console.anthropic.com",
              REGION_GLOBAL,
              "走 muniu anthropic 桥接(裸域,转发拼 /v1/messages),入站 OpenAI 形态自动翻译;"
                  + "模型代际迭代快,上线时以官方 /v1/models 复核"),
          new Preset(
              "openai",
              "OpenAI 官方",
              ADAPTER_OPENAI,
              "https://api.openai.com/v1",
              "OPENAI_API_KEY",
              List.of("gpt-5.2", "gpt-5.1", "gpt-5-mini"),
              "https://platform.openai.com",
              REGION_GLOBAL,
              "OpenAI 兼容基座的参照实现;当期旗舰 GPT-5.2"),
          new Preset(
              "xai",
              "xAI(Grok)官方",
              ADAPTER_OPENAI,
              "https://api.x.ai/v1",
              "XAI_API_KEY",
              List.of("grok-4", "grok-4-fast-reasoning"),
              "https://console.x.ai",
              REGION_GLOBAL,
              "OpenAI 兼容端点;代际迭代快"),
          new Preset(
              "gemini",
              "Google Gemini 官方(OpenAI 兼容层)",
              ADAPTER_OPENAI,
              "https://generativelanguage.googleapis.com/v1beta/openai",
              "GEMINI_API_KEY",
              List.of("gemini-2.5-pro", "gemini-2.5-flash"),
              "https://aistudio.google.com",
              REGION_GLOBAL,
              "官方 OpenAI 兼容层(base_url 以 /openai 收尾),为受限子集;muniu 不建原生桥接"),
          new Preset(
              "openrouter",
              "OpenRouter",
              ADAPTER_OPENAI,
              "https://openrouter.ai/api/v1",
              "OPENROUTER_API_KEY",
              List.of("anthropic/claude-sonnet-4.5", "openai/gpt-5.2"),
              "https://openrouter.ai",
              REGION_GLOBAL,
              "聚合网关官方端点(非厂商第一方),模型 ID 带 vendor/ 前缀,全量清单以其 GET /models 为准"),
          new Preset(
              "groq",
              "Groq",
              ADAPTER_OPENAI,
              "https://api.groq.com/openai/v1",
              "GROQ_API_KEY",
              List.of("llama-3.3-70b-versatile", "openai/gpt-oss-120b"),
              "https://console.groq.com",
              REGION_GLOBAL,
              "开源模型高速推理云,模型 ID 裸名与带前缀混排"),
          new Preset(
              "mistral",
              "Mistral AI 官方",
              ADAPTER_OPENAI,
              "https://api.mistral.ai/v1",
              "MISTRAL_API_KEY",
              List.of("mistral-large-latest", "codestral-latest"),
              "https://console.mistral.ai",
              REGION_GLOBAL,
              "OpenAI 兼容端点,-latest 为滚动别名"));

  private ProviderPresetCatalog() {}

  public static List<Preset> presets() {
    return PRESETS;
  }

  public static PresetEnvelope envelope() {
    return new PresetEnvelope(PRESETS);
  }
}
