package com.longzhong.jinguan.user;

import com.longzhong.jinguan.security.JwtService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员维护本地授权：角色与禁用；不下发到虎符。 */
@RestController
@RequestMapping("/admin/users")
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminPrincipalController {

  private static final Logger LOG = LoggerFactory.getLogger(AdminPrincipalController.class);

  /** 角色变更请求。 */
  public record RoleRequest(@NotBlank @Pattern(regexp = "USER|ADMIN") String role) {}

  private final PrincipalService principals;
  private final RefreshTokenService refreshTokens;

  public AdminPrincipalController(PrincipalService principals, RefreshTokenService refreshTokens) {
    this.principals = principals;
    this.refreshTokens = refreshTokens;
  }

  @PostMapping("/{subject}/role")
  public ResponseEntity<Void> role(
      @PathVariable String subject,
      @RequestBody @Validated RoleRequest req,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    principals.grantRole(subject, req.role());
    LOG.info(
        "principal role changed actor={} subject={} role={}", actor.subject(), subject, req.role());
    return ResponseEntity.noContent().build();
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
