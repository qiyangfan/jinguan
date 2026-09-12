package com.longzhong.jinguan.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** mock 渠道订单:状态机 CREATED → PAID(意图方法 markPaid),演示渠道专用。 */
@Entity
@Table(name = "mock_payment_orders")
public class MockOrderEntity {

  @Id
  @Column(name = "order_id", length = 64)
  private String orderId;

  @Column(name = "user_id", nullable = false)
  private long userId;

  @Column(name = "amount_micros", nullable = false)
  private long amountMicros;

  @Column(nullable = false, length = 8)
  private String currency;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  protected MockOrderEntity() {} // JPA

  public static MockOrderEntity create(
      String orderId, long userId, long amountMicros, String currency, Instant expiresAt) {
    MockOrderEntity o = new MockOrderEntity();
    o.orderId = orderId;
    o.userId = userId;
    o.amountMicros = amountMicros;
    o.currency = currency;
    o.status = "CREATED";
    o.createdAt = Instant.now();
    o.expiresAt = expiresAt;
    return o;
  }

  /** 支付成功(终态):重复调用幂等无害;返回是否发生首次转换(用于事件发布判定)。 */
  public boolean markPaid() {
    if ("PAID".equals(status)) {
      return false;
    }
    status = "PAID";
    paidAt = Instant.now();
    return true;
  }

  /** 过期判定:仅 CREATED 形态可过期(PAID/EXPIRED 为终态)。 */
  public boolean isExpired(Instant now) {
    return "CREATED".equals(status) && expiresAt != null && now.isAfter(expiresAt);
  }

  /** 过期收敛(终态):仅 CREATED 形态可迁移(扫描任务调用)。 */
  public void expire() {
    if ("CREATED".equals(status)) {
      status = "EXPIRED";
    }
  }

  public boolean isPaid() {
    return "PAID".equals(status);
  }

  public String getOrderId() {
    return orderId;
  }

  public long getUserId() {
    return userId;
  }

  public long getAmountMicros() {
    return amountMicros;
  }

  public String getCurrency() {
    return currency;
  }

  public String getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getPaidAt() {
    return paidAt;
  }
}
