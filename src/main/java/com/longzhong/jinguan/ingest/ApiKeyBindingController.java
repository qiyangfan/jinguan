package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.common.KeyRevokedException;
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

/** 管理 muniu key 与用户的绑定；终态吊销的 key 不可重新绑定。 */
@RestController
@RequestMapping("/admin/key-bindings")
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class ApiKeyBindingController {

  public record BindRequest(@NotBlank String keyId, @Positive long userId) {}

  private final ApiKeyBindingRepository bindings;
  private final KeyRevocationRepository revocations;

  public ApiKeyBindingController(
      ApiKeyBindingRepository bindings, KeyRevocationRepository revocations) {
    this.bindings = bindings;
    this.revocations = revocations;
  }

  @PostMapping
  public ResponseEntity<Map<String, Object>> bind(@RequestBody @Validated BindRequest req) {
    if (revocations.existsByKeyId(req.keyId())) {
      throw new KeyRevokedException(req.keyId());
    }
    bindings.save(new ApiKeyBindingEntity(req.keyId(), req.userId()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("keyId", req.keyId(), "userId", String.valueOf(req.userId())));
  }
}
