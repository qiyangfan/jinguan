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
      String providerName, long userId, long amountCredits, String currency) {
    if (amountCredits <= 0) {
      throw new IllegalArgumentException("金额必须为正(credits)");
    }
    String orderId = "po-" + uuidV7Safe();
    PaymentProvider.CreatedOrder created =
        provider(providerName).createOrder(orderId, userId, amountCredits, currency);
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

  /**
   * 处理渠道回调(经渠道适配器验签):订单状态原子迁移 CREATED → PAID(条件 UPDATE 行锁串行化并发回调), 仅首次转换发布支付成功事件。超时守门(懒过期):过期订单拒绝置
   * PAID——资金正确性不依赖扫描调度, 即使收敛任务延迟,过期支付也不会入账。幂等:已 PAID 订单重推回调直接返回成功(replayed)。
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
    Instant paidAt = Instant.now();
    int updated = mockOrders.markPaidIfCreated(orderId, paidAt);
    if (updated == 0) {
      // 并发败方/重推:确认终态——PAID 走幂等重放;窗口内被扫描收敛为 EXPIRED 则仍守门拒绝
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
    // 仅首次原子转换发布入账事件(并发/重推回调不产生重复事件);订单行锁已串行化,同 refId 不会并发入账
    events.publishEvent(
        new PaymentPaidEvent(
            orderId, order.getUserId(), order.getAmountCredits(), order.getCurrency(), paidAt));
    return Map.of("orderId", orderId, "status", "PAID", "replayed", false);
  }

  /** 订单详情(本人):非本人订单以 404 拒绝(不泄露存在性)。 */
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

  /** 本人订单簿(新→旧,分页;orderId 含时间前缀,字典序即时序)。 */
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
      // 水平越权:与非存在订单同响应(404),不泄露他人订单存在性
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
