package com.longzhong.jinguan.command;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 命令视图映射:params/result JSON 解析。 */
@Component
class CommandViewMapper {

  private final ObjectMapper mapper;

  CommandViewMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  CommandService.CommandView viewOf(ControlCommandEntity command) {
    return new CommandService.CommandView(
        command.getId(),
        command.getServiceId(),
        command.getAction(),
        command.getActionSchemaVersion(),
        parse(command.getParamsJson()),
        command.getReason(),
        command.getActor(),
        command.getStatus(),
        command.getAttempts(),
        command.getMaxAttempts(),
        command.getClaimedBy(),
        parse(command.getResultJson()),
        command.getError(),
        command.getCreatedAt(),
        command.getUpdatedAt(),
        command.getFinishedAt());
  }

  private JsonNode parse(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return mapper.readTree(json);
    } catch (RuntimeException e) {
      return null;
    }
  }
}
