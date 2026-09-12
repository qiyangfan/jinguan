/* 价目域 API:管理面 CRUD + 面向 muniu 的下发契约端点(匿名 GET)。 */

import { api } from "./api";
import type { PricingEnvelope, PricingUpsert, PricingView } from "./api-types.gen";

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
