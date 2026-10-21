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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAdapter() {
    return adapter;
  }

  public void setAdapter(String adapter) {
    this.adapter = adapter;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getKeysJson() {
    return keysJson;
  }

  public void setKeysJson(String keysJson) {
    this.keysJson = keysJson;
  }

  public long getWeight() {
    return weight;
  }

  public void setWeight(long weight) {
    this.weight = weight;
  }

  public long getPriority() {
    return priority;
  }

  public void setPriority(long priority) {
    this.priority = priority;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
