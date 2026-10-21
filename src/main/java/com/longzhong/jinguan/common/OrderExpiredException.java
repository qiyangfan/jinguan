package com.longzhong.jinguan.common;

/** 订单已过支付有效期，回调拒收；真实渠道对应“订单已关闭”回执，渠道不会重推。 */
public class OrderExpiredException extends DomainException {

  public OrderExpiredException(String orderId) {
    super("订单已过期: " + orderId);
  }
}
