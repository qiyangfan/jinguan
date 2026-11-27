package com.longzhong.jinguan.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/** X-01 命令 action 注册表:从随包契约 schema 加载,每个 action 独立版本。 */
@Component
public class CommandActionCatalog {

  private static final String LOCATION =
      "classpath*:contracts/control-plane/command-actions/*.schema.json";

  private final Map<String, Compiled> actions = new HashMap<>();
  private final com.fasterxml.jackson.databind.ObjectMapper jackson2 =
      new com.fasterxml.jackson.databind.ObjectMapper();

  public CommandActionCatalog() {
    JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    try {
      Resource[] resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
      for (Resource resource : resources) {
        load(factory, resource);
      }
    } catch (IOException e) {
      throw new IllegalStateException("加载命令 action schema 失败", e);
    }
    if (actions.isEmpty()) {
      throw new IllegalStateException("未找到任何命令 action schema");
    }
  }

  private void load(JsonSchemaFactory factory, Resource resource) throws IOException {
    JsonNode root;
    try (InputStream in = resource.getInputStream()) {
      root = jackson2.readTree(in.readAllBytes());
    }
    String action = root.path("x-action").asText();
    int version = root.path("x-version").asInt();
    if (action.isBlank() || version <= 0) {
      throw new IllegalStateException("action schema 缺少 x-action/x-version: " + resource);
    }
    String key = key(action, version);
    if (actions.putIfAbsent(key, new Compiled(action, version, factory.getSchema(root))) != null) {
      throw new IllegalStateException("重复的 action schema: " + key);
    }
  }

  /** 校验 params;未知 action/版本或参数不合法抛 IllegalArgumentException(400,不落库)。 */
  public void validate(String action, int version, tools.jackson.databind.JsonNode params) {
    Compiled compiled = actions.get(key(action, version));
    if (compiled == null) {
      throw new IllegalArgumentException("未知命令 action 或版本: " + action + "@" + version);
    }
    JsonNode json2;
    try {
      json2 = jackson2.readTree(params == null ? "{}" : params.toString());
    } catch (IOException e) {
      throw new IllegalArgumentException("params 不是合法 JSON", e);
    }
    Set<ValidationMessage> errors = compiled.schema().validate(json2);
    if (!errors.isEmpty()) {
      String detail =
          errors.stream()
              .limit(3)
              .map(ValidationMessage::getMessage)
              .reduce((a, b) -> a + "; " + b)
              .orElse("参数不合法");
      throw new IllegalArgumentException("params 不符合 action schema: " + detail);
    }
  }

  /** 已注册的 action@version 列表。 */
  public Set<String> registered() {
    return new TreeSet<>(actions.keySet());
  }

  public boolean supports(String action, int version) {
    return actions.containsKey(key(action, version));
  }

  private static String key(String action, int version) {
    return String.format(Locale.ROOT, "%s@%d", action, version);
  }

  private record Compiled(String action, int version, JsonSchema schema) {}
}
