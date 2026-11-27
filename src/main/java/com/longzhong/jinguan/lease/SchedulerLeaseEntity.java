package com.longzhong.jinguan.lease;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 调度器租约:同一任务同时只有一个持有者,过期可被抢占。 */
@Entity
@Table(name = "scheduler_leases")
public class SchedulerLeaseEntity {

  @Id
  @Column(nullable = false, length = 64)
  private String name;

  @Column(nullable = false, length = 128)
  private String owner;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA 无参构造。 */
  protected SchedulerLeaseEntity() {}

  static SchedulerLeaseEntity held(String name, String owner, Instant expiresAt) {
    SchedulerLeaseEntity entity = new SchedulerLeaseEntity();
    entity.name = name;
    entity.owner = owner;
    entity.expiresAt = expiresAt;
    entity.updatedAt = Instant.now();
    return entity;
  }

  String getName() {
    return name;
  }

  public String getOwner() {
    return owner;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  void takeOver(String newOwner, Instant expiresAt) {
    this.owner = newOwner;
    this.expiresAt = expiresAt;
    this.updatedAt = Instant.now();
  }
}
