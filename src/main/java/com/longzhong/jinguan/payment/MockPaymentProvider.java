package com.longzhong.jinguan.payment;

import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 演示用支付渠道；回调需匹配订单号且状态为 PAID，不验证真实签名。 */
@Component
public class MockPaymentProvider implements PaymentProvider {

  public static final String NAME = "mock";

  private final MockOrderRepository orders;
  private final java.time.Duration orderTtl;

  public MockPaymentProvider(
      MockOrderRepository orders,
      @Value("${jinguan.payment.order-ttl-minutes:60}") long ttlMinutes) {
    this.orders = orders;
    this.orderTtl = java.time.Duration.ofMinutes(ttlMinutes);
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public CreatedOrder createOrder(
      String orderId, long userId, long amountCredits, String currency) {
    Instant expiresAt = Instant.now().plus(orderTtl);
    orders.save(MockOrderEntity.create(orderId, userId, amountCredits, currency, expiresAt));
    return new CreatedOrder(
        "mock-" + UUID.randomUUID().toString().substring(0, 8),
        "/payments/mock/%s/pay".formatted(orderId),
        expiresAt);
  }

  @Override
  public boolean verifyCallback(String orderId, CallbackPayload payload) {
    if (payload == null || !orderId.equals(payload.orderId())) {
      return false;
    }
    return "PAID".equalsIgnoreCase(payload.status());
  }

  @Override
  public String queryStatus(String orderId) {
    return orders.findById(orderId).map(MockOrderEntity::getStatus).orElse("UNKNOWN");
  }
}
