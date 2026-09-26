/* 拓扑域 API:三件套(供应商/模型路由/能力目录)整包读写 + 官方厂商预设目录。
   载荷形态与 muniu TopologyPayload 逐字段同构(snake_case 契约);key 只存 env 引用名绝不明文。
   预设是后端 ProviderPresetCatalog 的投影(官方第一方端点,adapter ⊆ muniu 支持集)。 */

import { api } from "./api";

export interface KeyRef {
  env: string;
  weight: number;
}

export interface ProviderDef {
  name: string;
  adapter: string;
  base_url: string;
  keys: KeyRef[];
  weight: number;
  priority: number;
}

export interface RouteCandidate {
  provider: string;
  model: string;
  priority: number;
  weight: number;
}

export interface CapabilityDef {
  provider: string;
  model: string;
  drop_params: string[];
  thinking_lock: boolean;
  max_output_tokens: number;
}

export interface TopologyPayload {
  providers: ProviderDef[];
  model_routes: Record<string, RouteCandidate[]>;
  models: CapabilityDef[];
}

export interface ProviderPreset {
  name: string;
  display_name: string;
  adapter: string;
  base_url: string;
  env_suggestion: string;
  models: string[];
  website: string;
  region: string;
  note: string;
}

/** muniu 适配器类型全集(internal/adapter For 工厂;后端目录契约测试同源钉死)。 */
export const ADAPTERS = [
  "openai",
  "qwen",
  "doubao",
  "deepseek",
  "moonshot",
  "glm",
  "anthropic",
] as const;

/** 适配器中文名(下拉与卡片展示;anthropic 分支 = Claude 生态协议直连)。 */
export const ADAPTER_LABELS: Record<string, string> = {
  openai: "OpenAI 兼容",
  qwen: "通义千问",
  doubao: "火山方舟",
  deepseek: "DeepSeek",
  moonshot: "Kimi(月之暗面)",
  glm: "智谱 GLM",
  anthropic: "Anthropic 协议",
};

export function fetchTopology(): Promise<TopologyPayload> {
  return api.get<TopologyPayload>("/admin/topology");
}

/** 整包替换,返回新 ETag(与下发端点同源,内容哈希);校验拒绝走统一错误转译。 */
export function saveTopology(payload: TopologyPayload): Promise<string> {
  return api.putEtag("/admin/topology", payload);
}

export function fetchPresets(): Promise<{ presets: ProviderPreset[] }> {
  return api.get<{ presets: ProviderPreset[] }>("/admin/topology/presets");
}
