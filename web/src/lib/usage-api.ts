/* 用量明细 API；响应字段在此收窄。 */

import { client, unwrap } from "./api";

export interface UsageRecord {
  id: string;
  usageId: string;
  keyId: string;
  modelAlias: string | null;
  upstreamModel: string | null;
  status: string | null;
  promptTokens: number;
  completionTokens: number;
  reasoningTokens: number;
  cachedTokens: number;
  costCredits: string;
  currency: string | null;
  userId: string | null;
  finalOutcome: string; // accepted | replayed | pending(待结算) | skipped_unbound | skipped_free | skipped_insufficient
  attempts: number;
  lastAttemptAt: string;
}

export function fetchUsage(page: number, size: number): Promise<UsageRecord[]> {
  return unwrap(client.GET("/wallet/usage", { params: { query: { page, size } } }));
}
