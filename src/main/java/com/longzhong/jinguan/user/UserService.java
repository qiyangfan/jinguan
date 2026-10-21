package com.longzhong.jinguan.user;

import com.longzhong.jinguan.common.NotFoundException;
import com.longzhong.jinguan.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 处理用户注册、登录和密码重置；首位注册用户为 ADMIN。 */
@Service
public class UserService {

  public record AuthTokens(String access, String refresh) {}

  /** 用户不存在时用于执行一次 BCrypt 校验，降低登录响应时差。 */
  private static final String DUMMY_BCRYPT_HASH =
      "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public UserService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @Transactional
  public AuthTokens register(String username, String rawPassword) {
    if (users.existsByUsername(username)) {
      throw new IllegalArgumentException("用户名已存在: " + username);
    }
    String role = users.count() == 0 ? "ADMIN" : "USER";
    UserEntity user =
        users.save(UserEntity.create(username, passwordEncoder.encode(rawPassword), role));
    return issueFor(user);
  }

  @Transactional(readOnly = true)
  public AuthTokens login(String username, String rawPassword) {
    UserEntity user = users.findByUsername(username).orElse(null);
    // 统一错误信息，并对不存在的用户执行 BCrypt，避免泄露账号存在性和响应时差。
    if (user == null) {
      passwordEncoder.matches(rawPassword, DUMMY_BCRYPT_HASH);
      throw new IllegalArgumentException("用户名或密码错误");
    }
    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new IllegalArgumentException("用户名或密码错误");
    }
    return issueFor(user);
  }

  /** 管理员重置用户密码。 */
  @Transactional
  public void resetPassword(long userId, String rawPassword) {
    UserEntity user =
        users.findById(userId).orElseThrow(() -> new NotFoundException("用户不存在: " + userId));
    user.updatePasswordHash(passwordEncoder.encode(rawPassword));
    users.save(user);
  }

  @Transactional(readOnly = true)
  public long userId(String username) {
    return users
        .findByUsername(username)
        .map(UserEntity::getId)
        .orElseThrow(() -> new NotFoundException("用户不存在: " + username));
  }

  private AuthTokens issueFor(UserEntity user) {
    return new AuthTokens(
        jwtService.issueAccess(user.getId(), user.getUsername(), user.getRole()),
        jwtService.issueRefresh(user.getId(), user.getUsername(), user.getRole()));
  }
}
