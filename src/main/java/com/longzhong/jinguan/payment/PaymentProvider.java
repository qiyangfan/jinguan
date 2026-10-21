package com.longzhong.jinguan.payment;

import java.time.Instant;

/** 支付渠道端口；具体渠道通过适配器实现。 */
public interface PaymentProvider {

  String name();

  CreatedOrder createOrder(String orderId, long userId, long amountCredits, String currency);

  /** 校验渠道回调；返回 true 表示支付成功，无效载荷返回 false。 */
  boolean verifyCallback(String orderId, CallbackPayload payload);

  String queryStatus(String orderId);

  record CreatedOrder(String providerRef, String payUrl, Instant expiresAt) {}

  /** 已解析的渠道回调。 */
  record CallbackPayload(String orderId, String providerRef, String status, String rawSignature) {}
}
