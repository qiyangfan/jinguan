/* 支付域 API:下单/mock 渠道支付/订单查询/本人订单簿。
   响应为 Map 与 JPA 实体直出(契约中无稳定命名 schema),在此边界显式收窄;
   字段漂移由 CI openapi 漂移门禁兜底。 */

import { client, unwrap } from "./api";

export interface PaymentOrderCreated {
  orderId: string;
  provider: string;
  amountCredits: string;
  payUrl: string;
}

export interface PaymentCallbackResult {
  orderId: string;
  status: string; // CREATED | PAID | EXPIRED
  replayed: boolean;
}

export interface PaymentOrderQuery {
  orderId: string;
  providerStatus: string;
  status: string;
  amountCredits: string;
}

export interface PaymentOrderSummary {
  orderId: string;
  userId: number;
  amountCredits: string;
  currency: string;
  status: string; // CREATED | PAID | EXPIRED
  createdAt: string;
  paidAt: string | null;
  expiresAt: string | null;
}

export function createOrder(
  provider: string,
  amountCredits: number,
  currency: string,
): Promise<PaymentOrderCreated> {
  return unwrap(
    client.POST("/payments/orders", {
      body: { provider, amountCredits, currency },
    }),
  );
}

export function mockPay(orderId: string): Promise<PaymentCallbackResult> {
  return unwrap(client.POST("/payments/mock/{orderId}/pay", { params: { path: { orderId } } }));
}

export function queryOrder(orderId: string): Promise<PaymentOrderQuery> {
  return unwrap(client.GET("/payments/orders/{orderId}", { params: { path: { orderId } } }));
}

export function listOrders(page = 0, size = 50): Promise<PaymentOrderSummary[]> {
  return unwrap(client.GET("/payments/orders", { params: { query: { page, size } } }));
}
