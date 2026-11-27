package com.longzhong.jinguan.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 服务实例:能力/版本/心跳;注册 payload 不含 endpoint,地址由部署配置静态映射。 */
@Entity
@Table(name = "service_instances")
public class ServiceInstanceEntity {

  @Id
  @Column(nullable = false, length = 36)
  private String id;

  @Column(name = "service_id", nullable = false, length = 64)
  private String serviceId;

  @Column(name = "instance_id", nullable = false, length = 128)
  private String instanceId;

  @Column(nullable = false, length = 32)
  private String environment;

  @Column(nullable = false, length = 64)
  private String version;

  @Column(name = "capabilities_json", nullable = false, columnDefinition = "TEXT")
  private String capabilitiesJson;

  @Column(name = "admin_capabilities_json", nullable = false, columnDefinition = "TEXT")
  private String adminCapabilitiesJson;

  @Column(name = "registered_at", nullable = false)
  private Instant registeredAt;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  /** JPA 无参构造。 */
  protected ServiceInstanceEntity() {}

  static ServiceInstanceEntity register(
      String serviceId,
      String instanceId,
      String environment,
      String version,
      String capabilitiesJson,
      String adminCapabilitiesJson) {
    ServiceInstanceEntity entity = new ServiceInstanceEntity();
    entity.id = UUID.randomUUID().toString();
    entity.serviceId = serviceId;
    entity.instanceId = instanceId;
    entity.environment = environment;
    entity.version = version;
    entity.capabilitiesJson = capabilitiesJson;
    entity.adminCapabilitiesJson = adminCapabilitiesJson;
    entity.registeredAt = Instant.now();
    entity.lastSeenAt = entity.registeredAt;
    return entity;
  }

  void refresh(
      String nextEnvironment,
      String nextVersion,
      String capabilitiesJson,
      String adminCapabilitiesJson) {
    this.environment = nextEnvironment;
    this.version = nextVersion;
    this.capabilitiesJson = capabilitiesJson;
    this.adminCapabilitiesJson = adminCapabilitiesJson;
    this.lastSeenAt = Instant.now();
  }

  void touch() {
    this.lastSeenAt = Instant.now();
  }

  public String getId() {
    return id;
  }

  public String getServiceId() {
    return serviceId;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public String getEnvironment() {
    return environment;
  }

  public String getVersion() {
    return version;
  }

  public String getCapabilitiesJson() {
    return capabilitiesJson;
  }

  public String getAdminCapabilitiesJson() {
    return adminCapabilitiesJson;
  }

  public Instant getRegisteredAt() {
    return registeredAt;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }
}
