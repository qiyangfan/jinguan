package com.longzhong.jinguan.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 用户(支撑域,建模从简但纪律一致):无 public setter,状态变更走工厂与意图方法。 角色 USER | ADMIN;首个注册用户由 UserService 授
 * ADMIN(引导管理员)。
 */
@Entity
@Table(name = "users")
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 64)
  private String username;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(nullable = false, length = 16)
  private String role;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** 欠费停服标记(业务暂态):ingest 余额不足置位、入账清除;置位/恢复经 SuspensionService 条件 UPDATE 原子裁决。 */
  @Column(name = "suspended_at")
  private Instant suspendedAt;

  protected UserEntity() {} // JPA

  private UserEntity(String username, String passwordHash, String role) {
    this.username = username;
    this.passwordHash = passwordHash;
    this.role = role;
    this.createdAt = Instant.now();
  }

  /** 注册工厂:角色由应用层按注册序裁决(首用户 ADMIN)。 */
  public static UserEntity create(String username, String passwordHash, String role) {
    return new UserEntity(username, passwordHash, role);
  }

  /** 重置密码(ADMIN 通道):仅经意图方法变更,无 public setter。 */
  public void updatePasswordHash(String newPasswordHash) {
    this.passwordHash = newPasswordHash;
  }

  public Long getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getPasswordHash() {
    return passwordHash;
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
}
