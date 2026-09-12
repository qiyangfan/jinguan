/* 支付域 API:下单/mock 渠道支付/订单查询。 */

import { api } from "./api";
import type {
  PaymentCallbackResult,
  PaymentOrderCreated,
  PaymentOrderQuery,
} from "./api-types.gen";

export function createOrder(
  provider: string,
  amountMicros: number,
  currency: string,
): Promise<PaymentOrderCreated> {
  return api.post<PaymentOrderCreated>("/payments/orders", { provider, amountMicros, currency });
}

export function mockPay(orderId: string): Promise<PaymentCallbackResult> {
  return api.post<PaymentCallbackResult>(`/payments/mock/${orderId}/pay`);
}

export function queryOrder(orderId: string): Promise<PaymentOrderQuery> {
  return api.get<PaymentOrderQuery>(`/payments/orders/${orderId}`);
}
