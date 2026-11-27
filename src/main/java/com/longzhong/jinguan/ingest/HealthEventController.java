package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import com.longzhong.jinguan.common.StrictJson;
import com.longzhong.jinguan.registry.RegistryViews;
import com.longzhong.jinguan.registry.ServiceRegistryService;
import com.longzhong.jinguan.serviceidentity.ServiceAuthService;
import com.longzhong.jinguan.serviceidentity.ServicePrincipal;
import com.longzhong.jinguan.serviceidentity.ServiceScopes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 健康事件:只更新注册表 last_seen,不进入计费行。 */
@RestController
public class HealthEventController {

  private static final Set<String> HEALTH_FIELDS =
      Set.of("serviceId", "instanceId", "status", "observedAt");
  private static final Set<String> STATUSES = Set.of("UP", "DEGRADED");

  private final ServiceAuthService serviceAuth;
  private final ServiceRegistryService registry;
  private final AuditRecorder recorder;

  public HealthEventController(
      ServiceAuthService serviceAuth, ServiceRegistryService registry, AuditRecorder recorder) {
    this.serviceAuth = serviceAuth;
    this.registry = registry;
    this.recorder = recorder;
  }

  @PostMapping("/ingest/health")
  public RegistryViews.HeartbeatAck health(HttpServletRequest http, @RequestBody JsonNode body) {
    ServicePrincipal principal = serviceAuth.authenticate(http, ServiceScopes.USAGE);
    StrictJson.requireOnly(body, HEALTH_FIELDS);
    String serviceId = text(body, "serviceId");
    String instanceId = text(body, "instanceId");
    String status = text(body, "status").toUpperCase(java.util.Locale.ROOT);
    if (!STATUSES.contains(status)) {
      throw new IllegalArgumentException("status 只能是 UP/DEGRADED");
    }
    UsageEventService.requireOwnService(principal.serviceId(), serviceId);
    RegistryViews.HeartbeatAck ack = registry.heartbeat(principal, serviceId, instanceId);
    if (!"UP".equals(status)) {
      recorder.record(
          AuditActor.service(serviceId),
          AuditActions.HEALTH_RECEIVE,
          "service-instance",
          serviceId + "/" + instanceId,
          null,
          null,
          Map.of("status", status),
          "SUCCESS");
    }
    return ack;
  }

  private static String text(JsonNode body, String field) {
    JsonNode value = body.path(field);
    if (!value.isTextual() || value.asString().isBlank()) {
      throw new IllegalArgumentException(field + " 必填");
    }
    return value.asString();
  }
}
