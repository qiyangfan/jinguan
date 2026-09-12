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
}
