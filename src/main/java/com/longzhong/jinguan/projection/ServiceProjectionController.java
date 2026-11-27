package com.longzhong.jinguan.projection;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import com.longzhong.jinguan.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 服务只读视图:详情查看/导出写只读审计;不提供任何写入口。 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class ServiceProjectionController {

  private final ProjectionService projections;
  private final AuditRecorder recorder;

  public ServiceProjectionController(ProjectionService projections, AuditRecorder recorder) {
    this.projections = projections;
    this.recorder = recorder;
  }

  @GetMapping("/admin/services/{serviceId}/projection")
  public ProjectionService.ProjectionView view(
      @PathVariable String serviceId, @AuthenticationPrincipal JwtService.LoginUser actor) {
    ProjectionService.ProjectionView view = projections.get(serviceId);
    recorder.record(
        AuditActor.user(actor.subject(), actor.role()),
        AuditActions.PROJECTION_VIEW,
        "service-projection",
        serviceId,
        null,
        null,
        Map.of("state", view.state(), "mappingConfigured", view.mappingConfigured()),
        "SUCCESS");
    return view;
  }

  @GetMapping("/admin/services/{serviceId}/projection/export")
  public ResponseEntity<byte[]> export(
      @PathVariable String serviceId, @AuthenticationPrincipal JwtService.LoginUser actor) {
    String json = projections.exportJson(serviceId);
    recorder.record(
        AuditActor.user(actor.subject(), actor.role()),
        AuditActions.PROJECTION_EXPORT,
        "service-projection",
        serviceId,
        null,
        null,
        Map.of("bytes", json.getBytes(StandardCharsets.UTF_8).length),
        "SUCCESS");
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header("Content-Disposition", "attachment; filename=\"" + serviceId + "-projection.json\"")
        .body(json.getBytes(StandardCharsets.UTF_8));
  }
}
