package com.longzhong.jinguan.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/** 演示支付订单；状态从 CREATED 原子迁移到 PAID 或 EXPIRED。 */
@Entity
@Table(name = "mock_payment_orders")
public class MockOrderEntity {

  @Id
  @Column(name = "order_id", length = 64)
  private String orderId;

  @Column(name = "user_id", nullable = false)
  private long userId;

  @Column(name = "amount_credits", nullable = false)
  private long amountCredits;

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
      String orderId, long userId, long amountCredits, String currency, Instant expiresAt) {
    MockOrderEntity o = new MockOrderEntity();
    o.orderId = orderId;
    o.userId = userId;
    o.amountCredits = amountCredits;
    o.currency = currency;
    o.status = "CREATED";
    o.createdAt = Instant.now();
    o.expiresAt = expiresAt;
    return o;
  }

  /** 过期判定:仅 CREATED 形态可过期(PAID/EXPIRED 为终态)。 */
  public boolean isExpired(Instant now) {
    return "CREATED".equals(status) && expiresAt != null && now.isAfter(expiresAt);
  }

  public boolean isPaid() {
    return "PAID".equals(status);
  }

  public String getOrderId() {
    return orderId;
  }

  @JsonSerialize(using = ToStringSerializer.class)
  public long getUserId() {
    return userId;
  }

  @JsonSerialize(using = ToStringSerializer.class)
  public long getAmountCredits() {
    return amountCredits;
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
