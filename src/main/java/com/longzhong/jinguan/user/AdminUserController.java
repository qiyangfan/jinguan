package com.longzhong.jinguan.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理面:用户账号运维(密码约束与注册一致;重置后旧密码立即失效)。 */
@RestController
@RequestMapping("/admin/users")
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

  public record ResetPasswordRequest(@NotBlank @Size(min = 8, max = 72) String newPassword) {}

  private final UserService users;

  public AdminUserController(UserService users) {
    this.users = users;
  }

  @PostMapping("/{id}/reset-password")
  public ResponseEntity<Void> resetPassword(
      @PathVariable long id, @RequestBody @Validated ResetPasswordRequest req) {
    users.resetPassword(id, req.newPassword());
    return ResponseEntity.noContent().build();
  }
}
