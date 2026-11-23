package com.longzhong.jinguan.user;

import com.longzhong.jinguan.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员维护本地禁用状态;角色由 Keycloak client roles 管理。 */
@RestController
@RequestMapping("/admin/users")
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminPrincipalController {

  private static final Logger LOG = LoggerFactory.getLogger(AdminPrincipalController.class);

  private final PrincipalService principals;
  private final RefreshTokenService refreshTokens;

  public AdminPrincipalController(PrincipalService principals, RefreshTokenService refreshTokens) {
    this.principals = principals;
    this.refreshTokens = refreshTokens;
  }

  @PostMapping("/{subject}/disable")
  public ResponseEntity<Void> disable(
      @PathVariable String subject, @AuthenticationPrincipal JwtService.LoginUser actor) {
    principals.updateDisabled(subject, true);
    refreshTokens.revokeAll(subject);
    LOG.info("principal disabled actor={} subject={}", actor.subject(), subject);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{subject}/enable")
  public ResponseEntity<Void> enable(
      @PathVariable String subject, @AuthenticationPrincipal JwtService.LoginUser actor) {
    principals.updateDisabled(subject, false);
    LOG.info("principal enabled actor={} subject={}", actor.subject(), subject);
    return ResponseEntity.noContent().build();
  }
}
