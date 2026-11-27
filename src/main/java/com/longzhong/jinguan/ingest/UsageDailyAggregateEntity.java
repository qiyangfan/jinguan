package com.longzhong.jinguan.ingest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 用量日聚合:账单/统计保留;tenant/subject 用空串占位保证唯一键生效。 */
@Entity
@Table(name = "usage_daily_aggregates")
public class UsageDailyAggregateEntity {

  @Id
  @Column(nullable = false, length = 36)
  private String id;

  @Column(nullable = false, length = 64)
  private String source;

  @Column(name = "tenant_key", nullable = false, length = 64)
  private String tenantKey;

  @Column(nullable = false, length = 64)
  private String subject;

  @Column(nullable = false, length = 64)
  private String metric;

  @Column(name = "usage_day", nullable = false)
  private LocalDate day;

  @Column(nullable = false)
  private long quantity;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA 无参构造。 */
  protected UsageDailyAggregateEntity() {}

  static UsageDailyAggregateEntity of(
      String source,
      String tenantKey,
      String subject,
      String metric,
      LocalDate day,
      long quantity) {
    UsageDailyAggregateEntity entity = new UsageDailyAggregateEntity();
    entity.id = UUID.randomUUID().toString();
    entity.source = source;
    entity.tenantKey = tenantKey;
    entity.subject = subject;
    entity.metric = metric;
    entity.day = day;
    entity.quantity = quantity;
    entity.updatedAt = Instant.now();
    return entity;
  }

  void addQuantity(long delta) {
    this.quantity += delta;
    this.updatedAt = Instant.now();
  }

  public String getSource() {
    return source;
  }

  public String getTenantKey() {
    return tenantKey;
  }

  public String getSubject() {
    return subject;
  }

  public String getMetric() {
    return metric;
  }

  public LocalDate getDay() {
    return day;
  }

  public long getQuantity() {
    return quantity;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
