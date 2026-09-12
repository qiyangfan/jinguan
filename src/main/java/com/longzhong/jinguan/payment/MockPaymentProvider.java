package com.longzhong.jinguan.payment;

import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mock 支付渠道(演示):verifyCallback 接受一切合法形态载荷(模拟"渠道已验签"), 签名约定 = 简单对称自检(orderId 与 providerRef 同现即认),真实
 * PSP 的证书/签名校验 在其 adapter 内实现——端口契约不变。payUrl 指向本服务的模拟收银台回调端点。 订单时效(TTL)与真实渠道一致由本渠道维护:过期回调经守门拒绝。
 */
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
  public CreatedOrder createOrder(String orderId, long userId, long amountMicros, String currency) {
    Instant expiresAt = Instant.now().plus(orderTtl);
    orders.save(MockOrderEntity.create(orderId, userId, amountMicros, currency, expiresAt));
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
