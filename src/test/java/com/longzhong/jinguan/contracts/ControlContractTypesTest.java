package com.longzhong.jinguan.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longzhong.jinguan.command.CommandActionCatalog;
import com.longzhong.jinguan.ingest.UsageEventService;
import com.longzhong.jinguan.registry.ServiceRegistryService;
import com.longzhong.jinguan.snapshot.ControlSnapshot;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/** X-01 契约 harness(类型层):action 注册版本、DTO 字段对齐与未知 action 负向。 */
class ControlContractTypesTest {

  private static final Path ROOT = Path.of("src/main/resources/contracts/control-plane");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final tools.jackson.databind.ObjectMapper JACKSON3 =
      new tools.jackson.databind.ObjectMapper();

  @Test
  void commandActionCatalogRegistersEverySchemaWithVersions() throws Exception {
    CommandActionCatalog catalog = new CommandActionCatalog();
    Set<String> expected = new TreeSet<>();
    try (Stream<Path> paths = Files.walk(ROOT.resolve("command-actions"))) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".schema.json")).toList()) {
        JsonNode root = MAPPER.readTree(Files.readString(path));
        expected.add(root.path("x-action").asText() + "@" + root.path("x-version").asInt());
      }
    }
    assertEquals(expected, catalog.registered(), "action 注册表必须覆盖全部 schema 且版本独立");
  }

  @Test
  void unknownActionAndParamsAreRejectedBeforePersistence() {
    CommandActionCatalog catalog = new CommandActionCatalog();
    assertThrows(
        IllegalArgumentException.class,
        () -> catalog.validate("drop_everything", 1, null),
        "未知 action 必须拒绝");
    assertThrows(
        IllegalArgumentException.class,
        () -> catalog.validate("reindex", 9, null),
        "未知 action 版本必须拒绝");
    ObjectNode params = JACKSON3.createObjectNode();
    params.put("target", "everything");
    assertThrows(
        IllegalArgumentException.class,
        () -> catalog.validate("reindex", 1, params),
        "schema 外参数值必须拒绝");
  }

  @Test
  void snapshotDtoFieldsMatchContractSchema() throws Exception {
    JsonNode snapshot = MAPPER.readTree(Files.readString(ROOT.resolve("snapshot.schema.json")));
    Set<String> fields = new HashSet<>();
    for (RecordComponent component : ControlSnapshot.class.getRecordComponents()) {
      JsonProperty property = component.getAccessor().getAnnotation(JsonProperty.class);
      fields.add(property == null ? component.getName() : property.value());
    }
    assertEquals(snapshot.path("required").size(), fields.size(), "快照 DTO 字段必须与契约一致");
    for (JsonNode required : snapshot.path("required")) {
      assertTrue(fields.contains(required.asText()), "快照 DTO 缺少字段 " + required);
    }
  }

  @Test
  void registrationAndUsageDtoFieldsAreCoveredAndContainNoEndpoint() throws Exception {
    JsonNode registration =
        MAPPER.readTree(Files.readString(ROOT.resolve("service-registration.schema.json")));
    for (RecordComponent component :
        ServiceRegistryService.RegisterRequest.class.getRecordComponents()) {
      assertTrue(
          registration.path("properties").has(component.getName()),
          "注册 DTO 字段未入契约: " + component.getName());
    }
    assertFalse(registration.path("properties").has("endpoint"), "注册契约不得包含 endpoint");

    JsonNode usage = MAPPER.readTree(Files.readString(ROOT.resolve("usage-events.schema.json")));
    JsonNode eventProperties = usage.at("/properties/events/items/properties");
    for (RecordComponent component : UsageEventService.EventInput.class.getRecordComponents()) {
      assertTrue(eventProperties.has(component.getName()), "用量 DTO 字段未入契约: " + component.getName());
    }
  }
}
