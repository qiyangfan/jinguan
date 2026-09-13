/* 价目域 API:管理面 CRUD + 面向 muniu 的下发契约端点(匿名 GET)。
   请求体取自 openapi 契约类型;PricingView 为 snake_case 契约形态(muniu 逐字段对齐),
   在此以必需字段收窄(契约漂移由 CI openapi 漂移门禁兜底)。 */

import { api } from "./api";
import type { components } from "./api-types.gen";

type Schemas = components["schemas"];
export type PricingUpsert = Schemas["UpsertReq"];

export interface TimeWindow {
  window: string;
  multiplier: string;
}

export interface PricingView {
  alias: string;
  source: string;
  currency: string;
  input: string;
  cached_input: string;
  output: string;
  timezone: string | null;
  time_windows: TimeWindow[];
  updated_at: string;
}

export interface PricingEnvelope {
  pricing: PricingView[];
}

export function adminList(): Promise<PricingView[]> {
  return api.get<PricingView[]>("/admin/pricing");
}

export function upsert(alias: string, cmd: PricingUpsert): Promise<void> {
  return api.put(`/admin/pricing/${alias}`, cmd);
}

export function remove(alias: string): Promise<void> {
  return api.del(`/admin/pricing/${alias}`);
}

/** 控制面下发端点(与 muniu 拉取同形态;自测联调用)。 */
export function ingressList(): Promise<PricingEnvelope> {
  return api.get<PricingEnvelope>("/control/v1/pricing");
}
