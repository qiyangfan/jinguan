package com.longzhong.jinguan.controlversion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 控制快照版本水位;每次共享控制写操作在同一事务内递增。 */
@Entity
@Table(name = "control_versions")
public class ControlVersionEntity {

  @Id
  @Column(nullable = false, length = 32)
  private String id;

  @Column(nullable = false)
  private long version;

  @Column(name = "observed_at")
  private Instant observedAt;

  /** JPA 无参构造。 */
  protected ControlVersionEntity() {}

  public String getId() {
    return id;
  }

  public long getVersion() {
    return version;
  }

  public Instant getObservedAt() {
    return observedAt;
  }

  void increment(Instant observedAt) {
    version++;
    this.observedAt = observedAt;
  }
}
