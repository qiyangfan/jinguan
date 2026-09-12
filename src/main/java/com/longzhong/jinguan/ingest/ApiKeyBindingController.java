package com.longzhong.jinguan.ingest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理面:muniu key → 用户绑定(ingest 归账的身份映射)。 */
@RestController
@RequestMapping("/admin/key-bindings")
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class ApiKeyBindingController {

  public record BindRequest(@NotBlank String keyId, @Positive long userId) {}

  private final ApiKeyBindingRepository bindings;

  public ApiKeyBindingController(ApiKeyBindingRepository bindings) {
    this.bindings = bindings;
  }

  @PostMapping
  public ResponseEntity<Map<String, Object>> bind(@RequestBody @Validated BindRequest req) {
    bindings.save(new ApiKeyBindingEntity(req.keyId(), req.userId()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("keyId", req.keyId(), "userId", req.userId()));
  }
}
