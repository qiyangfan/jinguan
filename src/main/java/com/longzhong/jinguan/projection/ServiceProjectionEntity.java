package com.longzhong.jinguan.projection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 服务只读投影 last-known 缓存;payload 已脱敏。 */
@Entity
@Table(name = "service_projections")
public class ServiceProjectionEntity {

  @Id
  @Column(name = "service_id", nullable = false, length = 64)
  private String serviceId;

  @Column(length = 128)
  private String etag;

  @Column(length = 64)
  private String version;

  @Column(name = "observed_at")
  private Instant observedAt;

  @Column(name = "fetched_at", nullable = false)
  private Instant fetchedAt;

  @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
  private String payloadJson;

  /** JPA 无参构造。 */
  protected ServiceProjectionEntity() {}

  static ServiceProjectionEntity of(
      String serviceId, String etag, String version, Instant observedAt, String payloadJson) {
    ServiceProjectionEntity entity = new ServiceProjectionEntity();
    entity.serviceId = serviceId;
    entity.etag = etag;
    entity.version = version;
    entity.observedAt = observedAt;
    entity.fetchedAt = Instant.now();
    entity.payloadJson = payloadJson;
    return entity;
  }

  void refresh(String etag, String version, Instant observedAt, String payloadJson) {
    this.etag = etag;
    this.version = version;
    this.observedAt = observedAt;
    this.fetchedAt = Instant.now();
    this.payloadJson = payloadJson;
  }

  public String getServiceId() {
    return serviceId;
  }

  public String getEtag() {
    return etag;
  }

  public String getVersion() {
    return version;
  }

  public Instant getObservedAt() {
    return observedAt;
  }

  public Instant getFetchedAt() {
    return fetchedAt;
  }

  public String getPayloadJson() {
    return payloadJson;
  }
}
