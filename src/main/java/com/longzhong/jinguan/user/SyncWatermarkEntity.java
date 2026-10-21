package com.longzhong.jinguan.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 下发同步水位；名单变化与版本递增须同事务提交。 */
@Entity
@Table(name = "sync_watermarks")
public class SyncWatermarkEntity {

  @Id
  @Column(length = 32)
  private String name;

  @Column(nullable = false)
  private long version;

  protected SyncWatermarkEntity() {} // JPA

  private SyncWatermarkEntity(String name, long version) {
    this.name = name;
    this.version = version;
  }

  public static SyncWatermarkEntity of(String name, long version) {
    return new SyncWatermarkEntity(name, version);
  }

  public long version() {
    return version;
  }
}
