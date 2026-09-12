package com.longzhong.jinguan.user;

import com.longzhong.jinguan.common.NotFoundException;
import com.longzhong.jinguan.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户注册与登录;首注册用户为 ADMIN(单管理员引导),其余 USER。 */
@Service
public class UserService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public UserService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @Transactional
  public String register(String username, String rawPassword) {
    if (users.existsByUsername(username)) {
      throw new IllegalArgumentException("用户名已存在: " + username);
    }
    String role = users.count() == 0 ? "ADMIN" : "USER";
    UserEntity user =
        users.save(UserEntity.create(username, passwordEncoder.encode(rawPassword), role));
    return jwtService.issue(user.getId(), user.getUsername(), user.getRole());
  }

  @Transactional(readOnly = true)
  public String login(String username, String rawPassword) {
    UserEntity user =
        users
            .findByUsername(username)
            .orElseThrow(() -> new NotFoundException("用户不存在: " + username));
    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new IllegalArgumentException("密码错误");
    }
    return jwtService.issue(user.getId(), user.getUsername(), user.getRole());
  }

  @Transactional(readOnly = true)
  public long userId(String username) {
    return users
        .findByUsername(username)
        .map(UserEntity::getId)
        .orElseThrow(() -> new NotFoundException("用户不存在: " + username));
  }
}
