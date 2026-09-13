package com.longzhong.jinguan.topology;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/** 模型能力目录(声明制):(provider, model) 二元组精确声明,零内置零通配。 */
@Entity
@Table(name = "topology_models")
@IdClass(TopologyModelEntity.Pk.class)
public class TopologyModelEntity {

  /** (provider, model) 复合主键(JPA IdClass 形态:无参构造 + 属性名对齐)。 */
  public static class Pk implements Serializable {

    private String provider;
    private String model;

    public Pk() {}

    public Pk(String provider, String model) {
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
      if (this == o) return true;
      if (!(o instanceof Pk pk)) return false;
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

  public Pk id() {
    return new Pk(provider, model);
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getDropParamsJson() {
    return dropParamsJson;
  }

  public void setDropParamsJson(String dropParamsJson) {
    this.dropParamsJson = dropParamsJson;
  }

  public boolean isThinkingLock() {
    return thinkingLock;
  }

  public void setThinkingLock(boolean thinkingLock) {
    this.thinkingLock = thinkingLock;
  }

  public long getMaxOutputTokens() {
    return maxOutputTokens;
  }

  public void setMaxOutputTokens(long maxOutputTokens) {
    this.maxOutputTokens = maxOutputTokens;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
