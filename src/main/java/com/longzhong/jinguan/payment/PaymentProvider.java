package com.longzhong.jinguan.payment;

import java.time.Instant;

/**
 * 支付渠道出站端口(六边形):真实 PSP(微信/支付宝/Stripe…)未来各为一个适配器实现,
 * 领域层只依赖此抽象——新增渠道零改动账务与订单逻辑。当前唯一实现:MockPaymentProvider(演示)。
 */
public interface PaymentProvider {

  /** 渠道名(策略路由键;URL 与请求体引用)。 */
  String name();

  /** 创建支付单:渠道侧下单,返回渠道引用与支付跳转/凭证信息。 */
  CreatedOrder createOrder(String orderId, long userId, long amountCredits, String currency);

  /** 验签并解析回调载荷。真实渠道在此校验签名/证书;回调非法抛 IllegalArgumentException, 由调用方拒绝。返回 true 表示渠道确认支付成功(终态 PAID)。 */
  boolean verifyCallback(String orderId, CallbackPayload payload);

  /** 主动查询订单渠道侧状态(对账/补偿用)。 */
  String queryStatus(String orderId);

  /** 渠道侧订单引用(展示/对账)。 */
  record CreatedOrder(String providerRef, String payUrl, Instant expiresAt) {}

  /** 渠道回调载荷的已解析形态。 */
  record CallbackPayload(String orderId, String providerRef, String status, String rawSignature) {}
}
