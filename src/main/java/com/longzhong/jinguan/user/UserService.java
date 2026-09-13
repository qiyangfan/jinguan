package com.longzhong.jinguan.user;

import com.longzhong.jinguan.common.NotFoundException;
import com.longzhong.jinguan.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户注册/登录/密码重置;首注册用户为 ADMIN(单管理员引导),其余 USER。 */
@Service
public class UserService {

  /** 双 token:access(Bearer,前端内存)+ refresh(httpOnly cookie,轮换换发)。 */
  public record AuthTokens(String access, String refresh) {}

  /** 时序均衡 dummy:合法 BCrypt 串(明文无意义),仅供不存在用户走等时 matches。 */
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
    // 登录失败统一话术:不区分「用户不存在/密码错误」,不泄露用户名存在性(OWASP ASR 2.5.4)。
    // 时序均衡:用户不存在时也对固定 dummy hash 执行一次 BCrypt,抹平响应时差侧信道
    if (user == null) {
      passwordEncoder.matches(rawPassword, DUMMY_BCRYPT_HASH);
      throw new IllegalArgumentException("用户名或密码错误");
    }
    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new IllegalArgumentException("用户名或密码错误");
    }
    return issueFor(user);
  }

  /** 管理员重置密码:重置后旧密码立即失效(哈希覆盖)。 */
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
