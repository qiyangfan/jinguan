package com.longzhong.jinguan.registry;

import com.longzhong.jinguan.common.StrictJson;
import com.longzhong.jinguan.serviceidentity.ServiceAuthService;
import com.longzhong.jinguan.serviceidentity.ServicePrincipal;
import com.longzhong.jinguan.serviceidentity.ServiceScopes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 服务注册/心跳入口:服务身份认证 + 严格字段白名单(payload 不含 endpoint/凭据)。 */
@RestController
public class ServiceRegistryController {

  private static final String FIELD_SERVICE_ID = "serviceId";
  private static final String FIELD_INSTANCE_ID = "instanceId";

  private static final Set<String> REGISTER_FIELDS =
      Set.of(
          "serviceId", "instanceId", "environment", "version", "capabilities", "adminCapabilities");
  private static final Set<String> HEARTBEAT_FIELDS = Set.of(FIELD_SERVICE_ID, FIELD_INSTANCE_ID);

  private final ServiceAuthService serviceAuth;
  private final ServiceRegistryService registry;
  private final ObjectMapper mapper;

  public ServiceRegistryController(
      ServiceAuthService serviceAuth, ServiceRegistryService registry, ObjectMapper mapper) {
    this.serviceAuth = serviceAuth;
    this.registry = registry;
    this.mapper = mapper;
  }

  @PostMapping("/control/v1/registry/register")
  public RegistryViews.ServiceView register(HttpServletRequest http, @RequestBody JsonNode body) {
    final ServicePrincipal principal = serviceAuth.authenticate(http, ServiceScopes.REGISTRY);
    StrictJson.requireOnly(body, REGISTER_FIELDS);
    ServiceRegistryService.RegisterRequest request =
        mapper.treeToValue(body, ServiceRegistryService.RegisterRequest.class);
    requireText(request.serviceId(), FIELD_SERVICE_ID);
    requireText(request.instanceId(), FIELD_INSTANCE_ID);
    requireText(request.environment(), "environment");
    requireText(request.version(), "version");
    return registry.register(principal, request);
  }

  @PostMapping("/control/v1/registry/heartbeat")
  public RegistryViews.HeartbeatAck heartbeat(HttpServletRequest http, @RequestBody JsonNode body) {
    ServicePrincipal principal = serviceAuth.authenticate(http, ServiceScopes.REGISTRY);
    StrictJson.requireOnly(body, HEARTBEAT_FIELDS);
    String serviceId = requireText(body.path(FIELD_SERVICE_ID), FIELD_SERVICE_ID);
    String instanceId = requireText(body.path(FIELD_INSTANCE_ID), FIELD_INSTANCE_ID);
    return registry.heartbeat(principal, serviceId, instanceId);
  }

  /** 只允许契约字段;任何额外字段(如 endpoint)即 400。 */
  private static String requireText(JsonNode node, String field) {
    if (node == null || !node.isTextual() || node.asString().isBlank()) {
      throw new IllegalArgumentException(field + " 必填");
    }
    return node.asString();
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " 必填");
    }
    return value;
  }
}
