package com.longzhong.jinguan.payment;

import java.time.Instant;

/**
 * 支付成功领域事件(payment 模块对外契约):订单终态为 PAID,账务(wallet 模块)监听入账。 refId = "pay:" +
 * orderId,账本幂等键——回调重推/事件重放由账本唯一约束兜底。
 */
public record PaymentPaidEvent(
    String orderId, long userId, long amountCredits, String currency, Instant paidAt) {

  public String ledgerRefId() {
    return "pay:" + orderId;
  }
}
