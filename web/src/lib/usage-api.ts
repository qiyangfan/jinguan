/* 用量明细域 API:与账本流水经 refId="usage:{id}" 互相印证;
   响应为 JPA 实体直出(契约中无稳定命名 schema),在此边界显式收窄。 */

import { api } from "./api";

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
  return api.get<UsageRecord[]>(`/wallet/usage?page=${page}&size=${size}`);
}
