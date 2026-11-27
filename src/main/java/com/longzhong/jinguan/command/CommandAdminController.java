package com.longzhong.jinguan.command;

import com.longzhong.jinguan.common.StrictJson;
import com.longzhong.jinguan.security.JwtService;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
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

/** 控制台命令提交/查询;action/params 按 X-01 schema 校验,未知或非法即 400 不落库。 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class CommandAdminController {

  private static final String FIELD_SERVICE_ID = "serviceId";
  private static final String FIELD_ACTION = "action";
  private static final String FIELD_REASON = "reason";
  private static final String FIELD_IDEMPOTENCY_KEY = "idempotencyKey";

  private static final Set<String> SUBMIT_FIELDS =
      Set.of(
          FIELD_SERVICE_ID,
          FIELD_ACTION,
          "actionSchemaVersion",
          "params",
          FIELD_REASON,
          FIELD_IDEMPOTENCY_KEY);

  private final CommandService commands;
  private final CommandQueryService queries;
  private final ObjectMapper mapper;

  public CommandAdminController(
      CommandService commands, CommandQueryService queries, ObjectMapper mapper) {
    this.commands = commands;
    this.queries = queries;
    this.mapper = mapper;
  }

  @PostMapping("/admin/commands")
  public ResponseEntity<CommandService.CommandView> submit(
      @RequestBody JsonNode body, @AuthenticationPrincipal JwtService.LoginUser actor) {
    StrictJson.requireOnly(body, SUBMIT_FIELDS);
    String serviceId = text(body, FIELD_SERVICE_ID);
    String action = text(body, FIELD_ACTION);
    String reason = text(body, FIELD_REASON);
    int version = body.path("actionSchemaVersion").asInt(0);
    JsonNode params = body.path("params");
    String idempotencyKey =
        body.has(FIELD_IDEMPOTENCY_KEY) && !body.path(FIELD_IDEMPOTENCY_KEY).isNull()
            ? body.path(FIELD_IDEMPOTENCY_KEY).asString()
            : null;
    CommandService.SubmitResult result =
        commands.submit(
            serviceId,
            action,
            version,
            params.isMissingNode() || params.isNull() ? mapper.createObjectNode() : params,
            reason,
            actor.subject(),
            idempotencyKey);
    return ResponseEntity.ok()
        .header("X-Idempotent-Replay", Boolean.toString(result.replayed()))
        .body(result.command());
  }

  @GetMapping("/admin/commands")
  public List<CommandService.CommandView> list(
      @RequestParam(required = false) String serviceId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "100") int limit) {
    return queries.list(serviceId, status, limit);
  }

  @GetMapping("/admin/commands/{id}")
  public CommandService.CommandView get(@PathVariable String id) {
    return queries.get(id);
  }

  private static String text(JsonNode body, String field) {
    JsonNode value = body.path(field);
    if (!value.isTextual() || value.asString().isBlank()) {
      throw new IllegalArgumentException(field + " 必填");
    }
    return value.asString();
  }
}
