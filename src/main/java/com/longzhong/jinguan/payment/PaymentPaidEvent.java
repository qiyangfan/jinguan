package com.longzhong.jinguan.payment;

import java.time.Instant;

/** payment 模块的支付成功事件；wallet 据此入账，账本幂等键为 pay:{orderId}。 */
public record PaymentPaidEvent(
    String orderId, long userId, long amountCredits, String currency, Instant paidAt) {

  public String ledgerRefId() {
    return "pay:" + orderId;
  }
}
