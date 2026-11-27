package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import com.longzhong.jinguan.common.StrictJson;
import com.longzhong.jinguan.serviceidentity.ServiceAuthService;
import com.longzhong.jinguan.serviceidentity.ServicePrincipal;
import com.longzhong.jinguan.serviceidentity.ServiceScopes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 多来源用量接收:服务身份认证 + 严格字段白名单;kongming 的 LLM 用量整体拒绝。 */
@RestController
public class UsageEventController {

  private static final Set<String> ENVELOPE_FIELDS = Set.of("events");
  private static final Set<String> EVENT_FIELDS =
      Set.of(
          "eventId",
          "tenantKey",
          "subject",
          "entitlementKey",
          "metric",
          "quantity",
          "unit",
          "kind",
          "occurredAt");

  private final ServiceAuthService serviceAuth;
  private final UsageEventService usageEvents;
  private final AuditRecorder recorder;
  private final ObjectMapper mapper;

  public UsageEventController(
      ServiceAuthService serviceAuth,
      UsageEventService usageEvents,
      AuditRecorder recorder,
      ObjectMapper mapper) {
    this.serviceAuth = serviceAuth;
    this.usageEvents = usageEvents;
    this.recorder = recorder;
    this.mapper = mapper;
  }

  @PostMapping("/ingest/events")
  public UsageEventService.BatchResult accept(HttpServletRequest http, @RequestBody JsonNode body) {
    ServicePrincipal principal = serviceAuth.authenticate(http, ServiceScopes.USAGE);
    StrictJson.requireOnly(body, ENVELOPE_FIELDS);
    JsonNode events = body.path("events");
    if (!events.isArray()) {
      throw new IllegalArgumentException("events 必须是数组");
    }
    List<UsageEventService.EventInput> inputs = new ArrayList<>();
    events.forEach(
        element -> {
          StrictJson.requireOnly(element, EVENT_FIELDS);
          inputs.add(mapper.treeToValue(element, UsageEventService.EventInput.class));
        });
    try {
      return usageEvents.accept(principal.serviceId(), inputs);
    } catch (IllegalArgumentException e) {
      recorder.record(
          AuditActor.service(principal.serviceId()),
          AuditActions.USAGE_REJECT,
          "usage-batch",
          null,
          null,
          e.getMessage(),
          Map.of("events", inputs.size()),
          "DENIED");
      throw e;
    }
  }
}
