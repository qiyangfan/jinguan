package com.longzhong.jinguan.snapshot;

import com.longzhong.jinguan.serviceidentity.ServiceAuthService;
import com.longzhong.jinguan.serviceidentity.ServiceScopes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 控制快照端点:服务身份 + RS256 签名;命中 If-None-Match 返回 304。 */
@RestController
public class SnapshotController {

  static final String SIGNATURE_HEADER = "X-Jinguan-Signature";
  static final String KEY_ID_HEADER = "X-Jinguan-Key-Id";

  private final ServiceAuthService serviceAuth;
  private final SnapshotService snapshots;

  public SnapshotController(ServiceAuthService serviceAuth, SnapshotService snapshots) {
    this.serviceAuth = serviceAuth;
    this.snapshots = snapshots;
  }

  @GetMapping("/control/v1/snapshot")
  public ResponseEntity<String> snapshot(
      HttpServletRequest http,
      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
    var principal = serviceAuth.authenticate(http, ServiceScopes.SNAPSHOT);
    ControlSnapshot.Signed signed = snapshots.build(principal.serviceId());
    if (signed.etag().equals(ifNoneMatch)) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
          .header(HttpHeaders.ETAG, signed.etag())
          .header(SIGNATURE_HEADER, signed.signature())
          .header(KEY_ID_HEADER, signed.keyId())
          .build();
    }
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.ETAG, signed.etag())
        .header(SIGNATURE_HEADER, signed.signature())
        .header(KEY_ID_HEADER, signed.keyId())
        .body(signed.bodyJson());
  }
}
