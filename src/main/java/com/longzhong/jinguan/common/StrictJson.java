package com.longzhong.jinguan.common;

import java.util.HashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** 严格控制面请求体字段:未知字段直接 400,防止 endpoint/凭据等越界字段被静默接受。 */
public final class StrictJson {

  private StrictJson() {}

  public static void requireOnly(JsonNode body, Set<String> allowed) {
    if (body == null || !body.isObject()) {
      throw new IllegalArgumentException("请求体必须是 JSON 对象");
    }
    Set<String> unknown = new HashSet<>();
    for (String name : body.propertyNames()) {
      if (!allowed.contains(name)) {
        unknown.add(name);
      }
    }
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException("请求包含未允许字段: " + unknown);
    }
  }

  public static String requireText(JsonNode body, String field) {
    JsonNode value = body.path(field);
    if (!value.isTextual() || value.asString().isBlank()) {
      throw new IllegalArgumentException(field + " 必填");
    }
    return value.asString();
  }
}
