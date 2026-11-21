package com.longzhong.jinguan.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 本地授权主体：subject 为虎符 sub，本地只保存角色、禁用与停服状态。 */
@Entity
@Table(name = "principals")
public class PrincipalEntity {

  @Id
  @Column(length = 64)
  private String subject;

  @Column(nullable = false, unique = true, length = 64)
  private String username;

  @Column(nullable = false, length = 16)
  private String role;

  @Column(nullable = false)
  private boolean disabled;

  @Column(name = "suspended_at")
  private Instant suspendedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected PrincipalEntity() {} // JPA

  private PrincipalEntity(String subject, String username, String role) {
    this.subject = subject;
    this.username = username;
    this.role = role;
    this.createdAt = Instant.now();
  }

  /** 首登只建无权限资料；角色由管理员显式授予。 */
  public static PrincipalEntity jit(String subject, String username) {
    return new PrincipalEntity(subject, username, "USER");
  }

  public String getSubject() {
    return subject;
  }

  public String getUsername() {
    return username;
  }

  public String getRole() {
    return role;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getSuspendedAt() {
    return suspendedAt;
  }

  public boolean isDisabled() {
    return disabled;
  }

  /** 管理员调整本地角色；不下发到虎符。 */
  public void grantRole(String nextRole) {
    this.role = nextRole;
  }

  public void disable() {
    this.disabled = true;
  }

  public void enable() {
    this.disabled = false;
  }
}
