package com.longzhong.jinguan.payment;

import com.longzhong.jinguan.common.NotFoundException;
import com.longzhong.jinguan.common.OrderExpiredException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付应用服务:订单状态机(CREATED → PAID)按渠道策略路由;支付成功发布 PaymentPaidEvent(同事务),账务模块监听入账——支付与账本经事件解耦,幂等 由账本
 * refId 唯一约束兜底(回调重推安全)。
 */
@Service
public class PaymentService {

  private final Map<String, PaymentProvider> providers;
  private final MockOrderRepository mockOrders;
  private final ApplicationEventPublisher events;

  public PaymentService(
      java.util.List<PaymentProvider> providerList,
      MockOrderRepository mockOrders,
      ApplicationEventPublisher events) {
    this.providers =
        providerList.stream()
            .collect(Collectors.toUnmodifiableMap(PaymentProvider::name, Function.identity()));
    this.mockOrders = mockOrders;
    this.events = events;
  }

  public PaymentProvider provider(String name) {
    PaymentProvider p = providers.get(name);
    if (p == null) {
      throw new IllegalArgumentException("未知支付渠道: " + name);
    }
    return p;
  }

  /** 创建支付单:orderId 服务端生成(不信任客户端),渠道策略执行下单。 */
  @Transactional
  public Map<String, Object> createOrder(
      String providerName, long userId, long amountMicros, String currency) {
    if (amountMicros <= 0) {
      throw new IllegalArgumentException("金额必须为正(micros)");
    }
    String orderId = "po-" + uuidV7Safe();
    PaymentProvider.CreatedOrder created =
        provider(providerName).createOrder(orderId, userId, amountMicros, currency);
    return Map.of(
        "orderId",
        orderId,
        "provider",
        providerName,
        "amountMicros",
        amountMicros,
        "payUrl",
        created.payUrl());
  }

  /**
   * 处理渠道回调(经渠道适配器验签):订单 CREATED → PAID 并发布支付成功事件。 超时守门(懒过期):过期订单拒绝置 PAID——资金正确性不依赖扫描调度,
   * 即使收敛任务延迟,过期支付也不会入账。幂等:已 PAID 的订单重推回调直接返回成功。
   */
  @Transactional
  public Map<String, Object> handleCallback(
      String providerName, String orderId, PaymentProvider.CallbackPayload payload) {
    PaymentProvider provider = provider(providerName);
    if (!provider.verifyCallback(orderId, payload)) {
      throw new IllegalArgumentException("回调验签失败");
    }
    MockOrderEntity order =
        mockOrders.findById(orderId).orElseThrow(() -> new NotFoundException("订单不存在: " + orderId));
    if (order.isExpired(Instant.now()) || "EXPIRED".equals(order.getStatus())) {
      throw new OrderExpiredException(orderId);
    }
    if (order.isPaid()) {
      return Map.of("orderId", orderId, "status", "PAID", "replayed", true);
    }
    boolean firstTransition = order.markPaid();
    if (firstTransition) { // 仅首次转换发布入账事件(并发/重推回调不产生重复事件)
      events.publishEvent(
          new PaymentPaidEvent(
              order.getOrderId(),
              order.getUserId(),
              order.getAmountMicros(),
              order.getCurrency(),
              order.getPaidAt()));
    }
    return Map.of("orderId", orderId, "status", "PAID", "replayed", !firstTransition);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> query(String providerName, String orderId) {
    MockOrderEntity order =
        mockOrders.findById(orderId).orElseThrow(() -> new NotFoundException("订单不存在: " + orderId));
    return Map.of(
        "orderId",
        orderId,
        "providerStatus",
        provider(providerName).queryStatus(orderId),
        "status",
        order.getStatus(),
        "amountMicros",
        order.getAmountMicros());
  }

  /** 简易 UUIDv7 形态(时间前缀可排序);不强依赖外部库。 */
  static String uuidV7Safe() {
    long ms = System.currentTimeMillis();
    String mid = UUID.randomUUID().toString().replace("-", "");
    return String.format("%012x%s", ms, mid.substring(12));
  }
}
