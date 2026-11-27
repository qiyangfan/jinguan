package com.longzhong.jinguan.saga;

import com.longzhong.jinguan.common.StrictJson;
import com.longzhong.jinguan.security.JwtService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 跨域流程:启动/查看/关闭;只接受枚举流程类型与白名单参数。 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class SagaAdminController {

  private static final String FIELD_TYPE = "type";
  private static final String FIELD_REASON = "reason";
  private static final String FIELD_IDEMPOTENCY_KEY = "idempotencyKey";
  private static final String FIELD_PARAMS = "params";

  private static final Set<String> START_FIELDS =
      Set.of(FIELD_TYPE, FIELD_IDEMPOTENCY_KEY, FIELD_PARAMS, FIELD_REASON, "confirm");
  private static final Set<String> CLOSE_FIELDS = Set.of(FIELD_REASON);

  private final SagaService sagas;
  private final SagaQueryService queries;
  private final SagaLifecycleService lifecycle;
  private final ObjectMapper mapper;

  public SagaAdminController(
      SagaService sagas,
      SagaQueryService queries,
      SagaLifecycleService lifecycle,
      ObjectMapper mapper) {
    this.sagas = sagas;
    this.queries = queries;
    this.lifecycle = lifecycle;
    this.mapper = mapper;
  }

  @PostMapping("/admin/sagas")
  public SagaViews.SagaView start(
      @RequestBody JsonNode body, @AuthenticationPrincipal JwtService.LoginUser actor) {
    StrictJson.requireOnly(body, START_FIELDS);
    String type = text(body, FIELD_TYPE);
    String reason = text(body, FIELD_REASON);
    String idempotencyKey =
        body.has(FIELD_IDEMPOTENCY_KEY) && body.path(FIELD_IDEMPOTENCY_KEY).isTextual()
            ? body.path(FIELD_IDEMPOTENCY_KEY).asString()
            : null;
    boolean confirm = body.path("confirm").asBoolean(false);
    JsonNode paramsNode = body.path(FIELD_PARAMS);
    if (!paramsNode.isObject()) {
      throw new IllegalArgumentException("params 必须是 JSON 对象");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> params = mapper.convertValue(paramsNode, Map.class);
    return sagas.start(type, idempotencyKey, params, reason, confirm, actor.subject());
  }

  @GetMapping("/admin/sagas")
  public List<SagaViews.SagaView> list(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "100") int limit) {
    return queries.list(status, limit);
  }

  @GetMapping("/admin/sagas/{id}")
  public SagaViews.SagaView get(@PathVariable String id) {
    return queries.view(id);
  }

  @PostMapping("/admin/sagas/{id}/close")
  public SagaViews.SagaView close(
      @PathVariable String id,
      @RequestBody JsonNode body,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    StrictJson.requireOnly(body, CLOSE_FIELDS);
    return lifecycle.close(id, text(body, FIELD_REASON), actor.subject());
  }

  private static String text(JsonNode body, String field) {
    JsonNode value = body.path(field);
    if (!value.isTextual() || value.asString().isBlank()) {
      throw new IllegalArgumentException(field + " 必填");
    }
    return value.asString();
  }
}
