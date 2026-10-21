package com.longzhong.jinguan.payment;

import com.longzhong.jinguan.common.CommittedMetrics;
import com.longzhong.jinguan.common.MetricNames;
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

/** 管理支付订单与渠道回调；首次置付在事务内发布事件，由钱包模块入账。 */
@Service
public class PaymentService {

  private final Map<String, PaymentProvider> providers;
  private final MockOrderRepository mockOrders;
  private final ApplicationEventPublisher events;
  private final CommittedMetrics metrics;

  public PaymentService(
      java.util.List<PaymentProvider> providerList,
      MockOrderRepository mockOrders,
      ApplicationEventPublisher events,
      CommittedMetrics metrics) {
    this.providers =
        providerList.stream()
            .collect(Collectors.toUnmodifiableMap(PaymentProvider::name, Function.identity()));
    this.mockOrders = mockOrders;
    this.events = events;
    this.metrics = metrics;
  }

  public PaymentProvider provider(String name) {
    PaymentProvider p = providers.get(name);
    if (p == null) {
      throw new IllegalArgumentException("未知支付渠道: " + name);
    }
    return p;
  }

  /** 创建支付单并由服务端生成订单号。 */
  @Transactional
  public Map<String, Object> createOrder(
      String providerName, long userId, long amountCredits, String currency) {
    if (amountCredits <= 0) {
      throw new IllegalArgumentException("金额必须为正(credits)");
    }
    String orderId = "po-" + uuidV7Safe();
    PaymentProvider.CreatedOrder created =
        provider(providerName).createOrder(orderId, userId, amountCredits, currency);
    // 过期订单由扫描器单独计入 expiry.converged。
    metrics.countOnCommit(
        MetricNames.PAYMENT_ORDERS, MetricNames.TAG_EVENT, MetricNames.EVENT_CREATED);
    return Map.of(
        "orderId",
        orderId,
        "provider",
        providerName,
        "amountCredits",
        String.valueOf(amountCredits),
        "payUrl",
        created.payUrl());
  }

  /** 处理已验签的渠道回调；过期订单拒绝入账，重复回调返回幂等结果。 */
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
    Instant paidAt = Instant.now();
    int updated = mockOrders.markPaidIfCreated(orderId, paidAt);
    if (updated == 0) {
      // 并发回调或重试时重读最终状态。
      String status =
          mockOrders
              .findById(orderId)
              .map(MockOrderEntity::getStatus)
              .orElseThrow(() -> new NotFoundException("订单不存在: " + orderId));
      if ("EXPIRED".equals(status)) {
        throw new OrderExpiredException(orderId);
      }
      return Map.of("orderId", orderId, "status", status, "replayed", true);
    }
    metrics.countOnCommit(
        MetricNames.PAYMENT_ORDERS, MetricNames.TAG_EVENT, MetricNames.EVENT_PAID);
    events.publishEvent(
        new PaymentPaidEvent(
            orderId, order.getUserId(), order.getAmountCredits(), order.getCurrency(), paidAt));
    return Map.of("orderId", orderId, "status", "PAID", "replayed", false);
  }

  /** 查询本人订单；非本人订单返回 404。 */
  @Transactional(readOnly = true)
  public Map<String, Object> query(String providerName, String orderId, long requesterUid) {
    MockOrderEntity order = ownedOrder(orderId, requesterUid);
    return Map.of(
        "orderId",
        orderId,
        "providerStatus",
        provider(providerName).queryStatus(orderId),
        "status",
        order.getStatus(),
        "amountCredits",
        String.valueOf(order.getAmountCredits()));
  }

  /** 查询本人订单，新单在前。 */
  @Transactional(readOnly = true)
  public org.springframework.data.domain.Page<MockOrderEntity> list(
      long userId, int page, int size) {
    return mockOrders.findByUserIdOrderByOrderIdDesc(
        userId, org.springframework.data.domain.PageRequest.of(page, Math.min(size, 200)));
  }

  private MockOrderEntity ownedOrder(String orderId, long requesterUid) {
    MockOrderEntity order =
        mockOrders.findById(orderId).orElseThrow(() -> new NotFoundException("订单不存在: " + orderId));
    if (order.getUserId() != requesterUid) {
      // 越权访问他人订单时按不存在处理，避免泄露订单归属。
      throw new NotFoundException("订单不存在: " + orderId);
    }
    return order;
  }

  /** 简易 UUIDv7 形态(时间前缀可排序);不强依赖外部库。 */
  static String uuidV7Safe() {
    long ms = System.currentTimeMillis();
    String mid = UUID.randomUUID().toString().replace("-", "");
    return String.format("%012x%s", ms, mid.substring(12));
  }
}
