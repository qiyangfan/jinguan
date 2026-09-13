package com.longzhong.jinguan.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 控制面同步水位:{"name": 管道名 → 单调版本号}。任何进入下发名单的状态变化必须与 bump 同事务—— muniu 拉取侧仅应用 version &gt;= 本地的快照
 * (internal/auth/auth.go revocationLoop),漏 bump 即被当作旧快照忽略。 当前消费方:key-revocations
 * 管道(终态吊销/欠费停服置位/恢复); 后续管道(如拓扑下发)按 name 复用。
 */
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
