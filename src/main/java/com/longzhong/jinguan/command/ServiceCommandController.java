package com.longzhong.jinguan.command;

import com.longzhong.jinguan.common.StrictJson;
import com.longzhong.jinguan.serviceidentity.ServiceAuthService;
import com.longzhong.jinguan.serviceidentity.ServicePrincipal;
import com.longzhong.jinguan.serviceidentity.ServiceScopes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 服务侧命令通道:claim/ack 必须服务身份;匿名 401/403。 */
@RestController
public class ServiceCommandController {

  private static final String FIELD_LIMIT = "limit";
  private static final String FIELD_STATUS = "status";
  private static final String FIELD_RESULT = "result";
  private static final String FIELD_ERROR = "error";

  private static final Set<String> CLAIM_FIELDS = Set.of(FIELD_LIMIT);
  private static final Set<String> ACK_FIELDS = Set.of(FIELD_STATUS, FIELD_RESULT, FIELD_ERROR);

  private final ServiceAuthService serviceAuth;
  private final CommandService commands;
  private final CommandAckService acks;

  public ServiceCommandController(
      ServiceAuthService serviceAuth, CommandService commands, CommandAckService acks) {
    this.serviceAuth = serviceAuth;
    this.commands = commands;
    this.acks = acks;
  }

  @PostMapping("/control/v1/commands/claim")
  public List<CommandService.CommandView> claim(
      HttpServletRequest http, @RequestBody(required = false) JsonNode body) {
    ServicePrincipal principal = serviceAuth.authenticate(http, ServiceScopes.COMMAND);
    JsonNode safeBody = body == null ? null : body;
    if (safeBody != null) {
      StrictJson.requireOnly(safeBody, CLAIM_FIELDS);
    }
    int limit = safeBody == null ? 10 : safeBody.path(FIELD_LIMIT).asInt(10);
    return commands.claim(principal, limit);
  }

  @PostMapping("/control/v1/commands/{id}/ack")
  public CommandService.CommandView ack(
      HttpServletRequest http, @PathVariable String id, @RequestBody JsonNode body) {
    ServicePrincipal principal = serviceAuth.authenticate(http, ServiceScopes.COMMAND);
    StrictJson.requireOnly(body, ACK_FIELDS);
    String status = body.path(FIELD_STATUS).isTextual() ? body.path(FIELD_STATUS).asString() : null;
    JsonNode result = body.path(FIELD_RESULT);
    String error =
        body.has(FIELD_ERROR) && body.path(FIELD_ERROR).isTextual()
            ? body.path(FIELD_ERROR).asString()
            : null;
    return acks.ack(
        principal, id, status, result.isMissingNode() || result.isNull() ? null : result, error);
  }
}
