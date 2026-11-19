package com.longzhong.jinguan.topology;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/** 声明式模型能力目录，以 (provider, model) 精确匹配。 */
@Entity
@Table(name = "topology_models")
@IdClass(TopologyModelEntity.ModelKey.class)
public class TopologyModelEntity {

  /** JPA 复合主键，与实体字段同名。 */
  public static class ModelKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String provider;
    private String model;

    public ModelKey() {}

    public ModelKey(String provider, String model) {
      this.provider = provider;
      this.model = model;
    }

    public String getProvider() {
      return provider;
    }

    public String getModel() {
      return model;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ModelKey pk)) {
        return false;
      }
      return provider.equals(pk.provider) && model.equals(pk.model);
    }

    @Override
    public int hashCode() {
      return provider.hashCode() * 31 + model.hashCode();
    }
  }

  @Id
  @Column(nullable = false, length = 128)
  private String provider;

  @Id
  @Column(nullable = false, length = 255)
  private String model;

  @Column(name = "drop_params_json", columnDefinition = "TEXT")
  private String dropParamsJson;

  @Column(name = "thinking_lock", nullable = false)
  private boolean thinkingLock;

  @Column(name = "max_output_tokens", nullable = false)
  private long maxOutputTokens;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA 无参构造。 */
  protected TopologyModelEntity() {}

  private TopologyModelEntity(
      String provider,
      String model,
      String dropParamsJson,
      boolean thinkingLock,
      long maxOutputTokens,
      Instant updatedAt) {
    this.provider = provider;
    this.model = model;
    this.dropParamsJson = dropParamsJson;
    this.thinkingLock = thinkingLock;
    this.maxOutputTokens = maxOutputTokens;
    this.updatedAt = updatedAt;
  }

  static TopologyModelEntity of(
      String provider,
      String model,
      String dropParamsJson,
      boolean thinkingLock,
      long maxOutputTokens,
      Instant updatedAt) {
    return new TopologyModelEntity(
        provider, model, dropParamsJson, thinkingLock, maxOutputTokens, updatedAt);
  }

  public ModelKey id() {
    return new ModelKey(provider, model);
  }

  public String getProvider() {
    return provider;
  }

  public String getModel() {
    return model;
  }

  public String getDropParamsJson() {
    return dropParamsJson;
  }

  public boolean isThinkingLock() {
    return thinkingLock;
  }

  public long getMaxOutputTokens() {
    return maxOutputTokens;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
