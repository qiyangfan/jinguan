package com.longzhong.jinguan.topology;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 模型路由(运营态):alias → 有序候选数组(candidates_json 顺序即 failover 次序)。 */
@Entity
@Table(name = "topology_routes")
public class TopologyRouteEntity {

  @Id
  @Column(nullable = false, length = 128)
  private String alias;

  @Column(name = "candidates_json", nullable = false, columnDefinition = "TEXT")
  private String candidatesJson;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA 无参构造。 */
  protected TopologyRouteEntity() {}

  private TopologyRouteEntity(String alias, String candidatesJson, Instant updatedAt) {
    this.alias = alias;
    this.candidatesJson = candidatesJson;
    this.updatedAt = updatedAt;
  }

  static TopologyRouteEntity of(String alias, String candidatesJson, Instant updatedAt) {
    return new TopologyRouteEntity(alias, candidatesJson, updatedAt);
  }

  public String getAlias() {
    return alias;
  }

  public String getCandidatesJson() {
    return candidatesJson;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
