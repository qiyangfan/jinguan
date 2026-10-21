/* 钱包 API；在此收窄页面所需的余额、流水和管理操作字段。 */

import { client, unwrap } from "./api";
import type { components } from "./api-types.gen";

type Schemas = components["schemas"];
export type CreditRequest = Schemas["CreditRequest"];
export type DebitRequest = Schemas["DebitRequest"];

export interface WalletBalanceOut {
  userId: string;
  balanceCredits: string;
}

export interface LedgerEntryOut {
  id: string;
  userId: string;
  amountCredits: string;
  reason: string; // MANUAL_TOPUP | PAYMENT_TOPUP | MANUAL_ADJUST | USAGE
  refId: string;
  note: string | null;
  operatorUserId: string | null; // 管理员资金操作的操作人
  createdAt: string;
}

export function fetchBalance(): Promise<WalletBalanceOut> {
  return unwrap(client.GET("/wallet/balance"));
}

export function fetchEntries(page: number, size: number): Promise<LedgerEntryOut[]> {
  return unwrap(client.GET("/wallet/entries", { params: { query: { page, size } } }));
}

export function adminCredit(
  req: CreditRequest,
): Promise<{ refId: string; balanceCredits: number }> {
  return unwrap(client.POST("/wallet/admin/credit", { body: req }));
}

export function adminDebit(req: DebitRequest): Promise<{ refId: string; balanceCredits: number }> {
  return unwrap(client.POST("/wallet/admin/debit", { body: req }));
}
