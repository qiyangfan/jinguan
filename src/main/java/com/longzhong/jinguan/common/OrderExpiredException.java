package com.longzhong.jinguan.common;

/** 订单已过支付有效期:回调守门拒绝(真实渠道语义=回执"订单已关闭",渠道不重推)。 */
public class OrderExpiredException extends DomainException {

  public OrderExpiredException(String orderId) {
    super("订单已过期: " + orderId);
  }
}
