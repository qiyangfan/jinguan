/* 价目管理 API 和 muniu 下发契约。 */

import { client, unwrap } from "./api";
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
  return unwrap(client.GET("/admin/pricing"));
}

export function upsert(alias: string, cmd: PricingUpsert): Promise<void> {
  return unwrap(client.PUT("/admin/pricing/{alias}", { params: { path: { alias } }, body: cmd }));
}

export function remove(alias: string): Promise<void> {
  return unwrap(client.DELETE("/admin/pricing/{alias}", { params: { path: { alias } } }));
}

/** 控制面下发端点。 */
export function ingressList(): Promise<PricingEnvelope> {
  return unwrap(client.GET("/control/v1/pricing"));
}
