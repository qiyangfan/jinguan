package com.longzhong.jinguan.ingest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 多来源用量事件:幂等键 (source,event_id,metric);未聚合事件不清理。 */
@Entity
@Table(name = "usage_events")
public class UsageEventEntity {

  @Id
  @Column(nullable = false, length = 36)
  private String id;

  @Column(nullable = false, length = 64)
  private String source;

  @Column(name = "event_id", nullable = false, length = 128)
  private String eventId;

  @Column(name = "tenant_key", length = 64)
  private String tenantKey;

  @Column(length = 64)
  private String subject;

  @Column(name = "entitlement_key", length = 128)
  private String entitlementKey;

  @Column(nullable = false, length = 64)
  private String metric;

  @Column(nullable = false)
  private long quantity;

  @Column(nullable = false, length = 32)
  private String unit;

  @Column(nullable = false, length = 16)
  private String kind;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Column(name = "aggregated_at")
  private Instant aggregatedAt;

  /** JPA 无参构造。 */
  protected UsageEventEntity() {}

  static UsageEventEntity of(
      String source,
      String eventId,
      String tenantKey,
      String subject,
      String entitlementKey,
      String metric,
      long quantity,
      String unit,
      String kind,
      Instant occurredAt) {
    UsageEventEntity entity = new UsageEventEntity();
    entity.id = UUID.randomUUID().toString();
    entity.source = source;
    entity.eventId = eventId;
    entity.tenantKey = tenantKey;
    entity.subject = subject;
    entity.entitlementKey = entitlementKey;
    entity.metric = metric;
    entity.quantity = quantity;
    entity.unit = unit;
    entity.kind = kind;
    entity.occurredAt = occurredAt;
    entity.receivedAt = Instant.now();
    return entity;
  }

  void markAggregated() {
    this.aggregatedAt = Instant.now();
  }

  public String getSource() {
    return source;
  }

  public String getEventId() {
    return eventId;
  }

  public String getTenantKey() {
    return tenantKey;
  }

  public String getSubject() {
    return subject;
  }

  public String getEntitlementKey() {
    return entitlementKey;
  }

  public String getMetric() {
    return metric;
  }

  public long getQuantity() {
    return quantity;
  }

  public String getUnit() {
    return unit;
  }

  public String getKind() {
    return kind;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public Instant getAggregatedAt() {
    return aggregatedAt;
  }
}
