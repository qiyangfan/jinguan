package com.longzhong.jinguan.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 身份引导护栏:单行行锁序列化首注册,admin_assigned 记录首管理员席位是否已占用。 */
@Entity
@Table(name = "identity_bootstrap")
public class IdentityBootstrap {

  @Id private short id;

  @Column(name = "admin_assigned", nullable = false)
  private boolean adminAssigned;

  protected IdentityBootstrap() {} // JPA

  public short getId() {
    return id;
  }

  public boolean isAdminAssigned() {
    return adminAssigned;
  }

  /** 原子占用首管理员席位;已占用返回 false。调用方须持有该行写锁。 */
  public boolean claimAdmin() {
    if (adminAssigned) {
      return false;
    }
    adminAssigned = true;
    return true;
  }
}
