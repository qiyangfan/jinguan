package com.longzhong.jinguan.audit;

import com.longzhong.jinguan.security.JwtService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 控制面审计查询与导出;导出留只读审计,普通查询不留痕。 */
@RestController
@RequestMapping("/admin/audit-events")
@PreAuthorize("hasRole('ADMIN')")
public class ControlAuditController {

  private final ControlAuditQueryService query;
  private final AuditRecorder recorder;

  public ControlAuditController(ControlAuditQueryService query, AuditRecorder recorder) {
    this.query = query;
    this.recorder = recorder;
  }

  @SuppressWarnings("PMD.UseObjectForClearerAPI") // 筛选参数即查询契约,容器对象只增样板
  @GetMapping
  public List<ControlAuditQueryService.AuditView> search(
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String targetType,
      @RequestParam(required = false) String result,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return query.search(
        new ControlAuditQueryService.Filter(actor, action, targetType, result, from, to),
        page,
        size);
  }

  @SuppressWarnings("PMD.UseObjectForClearerAPI") // 筛选参数即查询契约,容器对象只增样板
  @GetMapping(value = "/export.csv", produces = "text/csv")
  public ResponseEntity<String> export(
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String targetType,
      @RequestParam(required = false) String result,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @AuthenticationPrincipal JwtService.LoginUser actorPrincipal) {
    var filter = new ControlAuditQueryService.Filter(actor, action, targetType, result, from, to);
    String csv = query.exportCsv(filter);
    recorder.record(
        AuditActor.user(actorPrincipal.subject(), actorPrincipal.role()),
        AuditActions.AUDIT_EXPORT,
        "control_audit",
        null,
        null,
        null,
        Map.of(
            "actor", actor == null ? "" : actor,
            "action", action == null ? "" : action,
            "targetType", targetType == null ? "" : targetType,
            "result", result == null ? "" : result),
        "SUCCESS");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv"))
        .header("Content-Disposition", "attachment; filename=\"control-audit.csv\"")
        .body(csv);
  }
}
