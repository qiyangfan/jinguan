/* 契约类型。⚠ 当前为手写占位形态(文件名/导入形态对齐生成物):
   jinguan 接入 springdoc → /v3/api-docs → openapi-typescript 契约管线后,
   本文件由 `pnpm contracts:gen` 重新生成,届时禁手改。 */

export interface components {
  schemas: {
    TokenOut: { token: string };
    WalletBalanceOut: { userId: number; balanceMicros: number };
    LedgerEntryOut: {
      id: number;
      userId: number;
      amountMicros: number;
      reason: string;
      refId: string;
      note: string | null;
      createdAt: string;
    };
    CreditRequest: { userId: number; amountMicros: number; refId?: string; note?: string };
    PaymentOrderCreated: {
      orderId: string;
      provider: string;
      amountMicros: number;
      payUrl: string;
    };
    PaymentCallbackResult: { orderId: string; status: string; replayed: boolean };
    PaymentOrderQuery: {
      orderId: string;
      providerStatus: string;
      status: string;
      amountMicros: number;
    };
    TimeWindow: { window: string; multiplier: string };
    PricingView: {
      alias: string;
      source: string;
      currency: string;
      input: string;
      cached_input: string;
      output: string;
      timezone?: string;
      time_windows: TimeWindow[];
      updated_at?: string;
    };
    PricingEnvelope: { pricing: PricingView[] };
    PricingUpsert: {
      currency: string;
      inputMicros: number;
      cachedInputMicros: number;
      outputMicros: number;
      timezone?: string;
      time_windows: TimeWindow[];
    };
    KeyBindingRequest: { keyId: string; userId: number };
    IngestResult: {
      received: number;
      accepted: number;
      replayed: number;
      skippedUnbound: number;
      skippedFree: number;
      skippedInsufficient: number;
    };
  };
}

export type TokenOut = components["schemas"]["TokenOut"];
export type WalletBalanceOut = components["schemas"]["WalletBalanceOut"];
export type LedgerEntryOut = components["schemas"]["LedgerEntryOut"];
export type CreditRequest = components["schemas"]["CreditRequest"];
export type PaymentOrderCreated = components["schemas"]["PaymentOrderCreated"];
export type PaymentCallbackResult = components["schemas"]["PaymentCallbackResult"];
export type PaymentOrderQuery = components["schemas"]["PaymentOrderQuery"];
export type TimeWindow = components["schemas"]["TimeWindow"];
export type PricingView = components["schemas"]["PricingView"];
export type PricingEnvelope = components["schemas"]["PricingEnvelope"];
export type PricingUpsert = components["schemas"]["PricingUpsert"];
export type KeyBindingRequest = components["schemas"]["KeyBindingRequest"];
export type IngestResult = components["schemas"]["IngestResult"];
