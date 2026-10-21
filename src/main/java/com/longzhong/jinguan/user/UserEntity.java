package com.longzhong.jinguan.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 用户账户；首位注册用户由 UserService 设为 ADMIN。 */
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

  /** 欠费停服时间；入账后清除。 */
  @Column(name = "suspended_at")
  private Instant suspendedAt;

  protected UserEntity() {} // JPA

  private UserEntity(String username, String passwordHash, String role) {
    this.username = username;
    this.passwordHash = passwordHash;
    this.role = role;
    this.createdAt = Instant.now();
  }

  /** 创建用户；角色由应用层决定。 */
  public static UserEntity create(String username, String passwordHash, String role) {
    return new UserEntity(username, passwordHash, role);
  }

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
