package com.longzhong.jinguan.saga;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 流程上下文 JSON 编解码;损坏即拒绝推进。 */
@Component
class SagaContextCodec {

  private final ObjectMapper mapper;

  SagaContextCodec(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  Map<String, Object> parse(String json) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> parsed = mapper.readValue(json, Map.class);
      return new LinkedHashMap<>(parsed == null ? Map.of() : parsed);
    } catch (JacksonException e) {
      throw new IllegalStateException("流程上下文损坏", e);
    }
  }

  String serialize(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JacksonException e) {
      throw new IllegalStateException("流程 JSON 序列化失败", e);
    }
  }

  JsonNode parseNode(String json) {
    if (json == null || json.isBlank()) {
      return mapper.createObjectNode();
    }
    try {
      return mapper.readTree(json);
    } catch (JacksonException e) {
      throw new IllegalStateException("流程 JSON 损坏", e);
    }
  }

  tools.jackson.databind.node.ObjectNode objectNode() {
    return mapper.createObjectNode();
  }
}
