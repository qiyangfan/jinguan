package com.longzhong.jinguan.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class UserController {

  private final UserService users;

  public UserController(UserService users) {
    this.users = users;
  }

  public record Credentials(
      @NotBlank @Size(min = 3, max = 64) String username,
      @NotBlank @Size(min = 8, max = 72) String password) {}

  @PostMapping("/auth/register")
  public ResponseEntity<Map<String, String>> register(@RequestBody @Validated Credentials req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("token", users.register(req.username(), req.password())));
  }

  @PostMapping("/auth/login")
  public Map<String, String> login(@RequestBody @Validated Credentials req) {
    return Map.of("token", users.login(req.username(), req.password()));
  }
}
