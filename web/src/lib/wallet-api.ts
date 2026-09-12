/* 钱包域 API:余额/账本流水/管理员手工入账。 */

import { api } from "./api";
import type { CreditRequest, LedgerEntryOut, WalletBalanceOut } from "./api-types.gen";

export function fetchBalance(): Promise<WalletBalanceOut> {
  return api.get<WalletBalanceOut>("/wallet/balance");
}

export function fetchEntries(page: number, size: number): Promise<LedgerEntryOut[]> {
  return api.get<LedgerEntryOut[]>(`/wallet/entries?page=${page}&size=${size}`);
}

export function adminCredit(req: CreditRequest): Promise<{ refId: string; balanceMicros: number }> {
  return api.post("/wallet/admin/credit", req);
}
