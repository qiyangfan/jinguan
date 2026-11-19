package com.longzhong.jinguan.topology;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 供应商定义；密钥仅保存环境变量引用。 */
@Entity
@Table(name = "topology_providers")
public class TopologyProviderEntity {

  @Id
  @Column(nullable = false, length = 128)
  private String name;

  @Column(nullable = false, length = 32)
  private String adapter;

  @Column(name = "base_url", nullable = false, length = 255)
  private String baseUrl;

  @Column(name = "keys_json", nullable = false, columnDefinition = "TEXT")
  private String keysJson;

  @Column(nullable = false)
  private long weight;

  @Column(nullable = false)
  private long priority;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA 无参构造。 */
  protected TopologyProviderEntity() {}

  private TopologyProviderEntity(
      String name,
      String adapter,
      String baseUrl,
      String keysJson,
      long weight,
      long priority,
      Instant updatedAt) {
    this.name = name;
    this.adapter = adapter;
    this.baseUrl = baseUrl;
    this.keysJson = keysJson;
    this.weight = weight;
    this.priority = priority;
    this.updatedAt = updatedAt;
  }

  static TopologyProviderEntity of(
      String name,
      String adapter,
      String baseUrl,
      String keysJson,
      long weight,
      long priority,
      Instant updatedAt) {
    return new TopologyProviderEntity(
        name, adapter, baseUrl, keysJson, weight, priority, updatedAt);
  }

  public String getName() {
    return name;
  }

  public String getAdapter() {
    return adapter;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getKeysJson() {
    return keysJson;
  }

  public long getWeight() {
    return weight;
  }

  public long getPriority() {
    return priority;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
