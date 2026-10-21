/* 拓扑 API；负载字段与 muniu 的 snake_case 契约一致，key 仅保存 env 引用名。 */

import { client, unwrap, unwrapEtag } from "./api";

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

/** muniu 支持的适配器类型。 */
export const ADAPTERS = [
  "openai",
  "qwen",
  "doubao",
  "deepseek",
  "moonshot",
  "glm",
  "anthropic",
] as const;

/** 适配器展示名称。 */
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
  return unwrap(client.GET("/admin/topology"));
}

/** 整包替换并返回新 ETag。 */
export function saveTopology(payload: TopologyPayload): Promise<string> {
  return unwrapEtag(client.PUT("/admin/topology", { body: payload }));
}

export function fetchPresets(): Promise<{ presets: ProviderPreset[] }> {
  return unwrap(client.GET("/admin/topology/presets"));
}
