package com.longzhong.jinguan.alerts;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import com.longzhong.jinguan.common.Redaction;
import com.longzhong.jinguan.common.UnauthorizedException;
import com.longzhong.jinguan.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 告警查询/确认(ADMIN)与运维事件上报(独立 token,供备份/探活脚本使用)。 */
@RestController
public class AlertController {

  private static final Set<String> OPERATIONAL_TYPES =
      Set.of(
          AlertTypes.CONTROL_UNAVAILABLE,
          AlertTypes.DB_FAILURE,
          AlertTypes.BACKUP_FAILURE,
          AlertTypes.CONTRACT_DRIFT);

  private final AlertService alerts;
  private final AuditRecorder recorder;
  private final AlertProperties properties;

  public AlertController(AlertService alerts, AuditRecorder recorder, AlertProperties properties) {
    this.alerts = alerts;
    this.recorder = recorder;
    this.properties = properties;
  }

  /** 运维事件上报请求;summary 会被脱敏后才入队。 */
  public record OperationalAlert(
      @NotBlank String type,
      String severity,
      String tenantKey,
      String serviceId,
      Map<String, Object> summary,
      String runbook) {

    /** 复制载荷,避免外部可变 Map 泄漏。 */
    public OperationalAlert {
      summary = summary == null ? Map.of() : Map.copyOf(summary);
    }
  }

  @GetMapping("/admin/alerts")
  @PreAuthorize("hasRole('ADMIN')")
  public List<AlertService.AlertView> list(@RequestParam(defaultValue = "100") int limit) {
    return alerts.list(limit);
  }

  @PostMapping("/admin/alerts/{id}/ack")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> ack(
      @PathVariable String id, @AuthenticationPrincipal JwtService.LoginUser actor) {
    alerts.ack(id, actor.subject());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/control/v1/alerts/operational")
  public ResponseEntity<Map<String, Object>> operational(
      @RequestHeader(value = "X-Operational-Token", required = false) String token,
      @Valid @RequestBody OperationalAlert request) {
    requireOperationalToken(token);
    if (!OPERATIONAL_TYPES.contains(request.type())) {
      throw new IllegalArgumentException("不支持的运维告警类型: " + request.type());
    }
    String severity = request.severity() == null ? "WARNING" : request.severity();
    boolean created =
        alerts.raise(
            request.type(),
            severity,
            "operational",
            request.tenantKey(),
            request.serviceId(),
            "operational:" + request.type() + ":" + UUID.randomUUID(),
            Redaction.redact(request.summary() == null ? Map.of() : request.summary()),
            request.runbook());
    recorder.record(
        AuditActor.system(),
        AuditActions.ALERT_RAISE,
        "alert",
        null,
        request.tenantKey(),
        "运维上报",
        Map.of("type", request.type(), "severity", severity),
        created ? "SUCCESS" : "DENIED");
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("accepted", true));
  }

  private void requireOperationalToken(String token) {
    String expected = properties.operationalToken();
    if (expected == null || expected.isBlank()) {
      throw new UnauthorizedException("运维告警通道未启用");
    }
    byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
    byte[] actualBytes = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
      throw new UnauthorizedException("运维 token 无效");
    }
  }
}
