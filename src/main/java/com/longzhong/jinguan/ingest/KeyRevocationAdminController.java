package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.security.JwtService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理面:key 终态吊销(泄露/封禁,key_id 永久作废)。幂等:重复吊销静默重放(replayed=true)不重复计数; 落库与水位 bump 的原子性由
 * KeyRevocationService 单事务保证。
 */
@RestController
@RequestMapping("/admin/keys")
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class KeyRevocationAdminController {

  public record RevokeRequest(@NotBlank @Size(max = 255) String reason) {}

  private final KeyRevocationService revocations;

  public KeyRevocationAdminController(KeyRevocationService revocations) {
    this.revocations = revocations;
  }

  @PostMapping("/{keyId}/revoke")
  public ResponseEntity<Map<String, Object>> revoke(
      @PathVariable @NotBlank @Size(max = 64) String keyId,
      @RequestBody @Validated RevokeRequest req,
      @AuthenticationPrincipal JwtService.LoginUser admin) {
    boolean replayed = revocations.revoke(keyId, req.reason(), admin.uid());
    if (replayed) {
      return ResponseEntity.ok(Map.of("keyId", keyId, "replayed", true));
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("keyId", keyId, "replayed", false));
  }
}
