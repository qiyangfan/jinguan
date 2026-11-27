package com.longzhong.jinguan.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** X-01 契约 harness(文件层):单一来源、版本、示例、敏感字段与编码;失败即阻断合并。 */
class ControlContractHarnessTest {

  private static final Map<String, String> EXAMPLES =
      Map.of(
          "snapshot-kongming.json", "snapshot.schema.json",
          "command.json", "command-envelope.schema.json",
          "command-ack.json", "command-ack.schema.json",
          "usage-events.json", "usage-events.schema.json",
          "health-event.json", "health-event.schema.json",
          "service-registration.json", "service-registration.schema.json",
          "service-read-projection.json", "service-read-projection.schema.json");

  @Test
  void catalogHashesMatchShippedSchemas() throws Exception {
    JsonNode catalog =
        ContractFiles.MAPPER.readTree(Files.readString(ContractFiles.ROOT.resolve("catalog.json")));
    assertEquals("v1", catalog.path("contractVersion").asText(), "契约版本应为 v1");
    Set<String> listed = new HashSet<>();
    List<String> mismatched = new ArrayList<>();
    for (JsonNode entry : catalog.path("contracts")) {
      String file = entry.path("file").asText();
      listed.add(file);
      if (!entry
          .path("sha256")
          .asText()
          .equals(ContractFiles.sha256(Files.readAllBytes(ContractFiles.ROOT.resolve(file))))) {
        mismatched.add(file);
      }
    }
    assertTrue(mismatched.isEmpty(), "契约变更必须同步 catalog.json 哈希: " + mismatched);
    assertEquals(ContractFiles.schemaFilesOnDisk(), listed, "catalog 必须列全所有 schema");
  }

  @Test
  void examplesValidateAgainstSchemas() throws Exception {
    for (var entry : EXAMPLES.entrySet()) {
      JsonSchema schema = ContractFiles.schemaOf(entry.getValue());
      JsonNode example =
          ContractFiles.MAPPER.readTree(
              Files.readString(ContractFiles.ROOT.resolve("examples").resolve(entry.getKey())));
      Set<ValidationMessage> errors = schema.validate(example);
      assertTrue(errors.isEmpty(), entry.getKey() + " 示例不合法: " + errors);
    }
  }

  @Test
  void negativeExamplesAreRejected() throws Exception {
    JsonSchema usage = ContractFiles.schemaOf("usage-events.schema.json");
    JsonNode negativeUsage =
        ContractFiles.MAPPER.readTree(
            """
            {"events":[{"eventId":"e1","metric":"bot.run","quantity":-1,"unit":"count",
              "occurredAt":"2026-11-27T08:00:00Z"}]}
            """);
    assertFalse(usage.validate(negativeUsage).isEmpty(), "负数量必须被 schema 拒绝");
    JsonSchema snapshot = ContractFiles.schemaOf("snapshot.schema.json");
    JsonNode missingSections =
        ContractFiles.MAPPER.readTree(
            """
            {"version":1,"observed_at":"2026-11-27T08:00:00Z","service_id":"kongming",
             "etag":"x","security_sections":["a","b","c"],"sections":{}}
            """);
    assertFalse(snapshot.validate(missingSections).isEmpty(), "缺失安全类 sections 必须被拒绝");
  }

  @Test
  void schemasCarryNoSensitiveFields() throws Exception {
    List<String> violations = ContractFiles.sensitivePropertyViolations();
    assertTrue(violations.isEmpty(), "契约 schema 不得含凭据/连接类字段: " + violations);
  }

  @Test
  void contractBytesAreUtf8AndParseable() throws IOException {
    try (Stream<Path> paths = Files.walk(ContractFiles.ROOT)) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        assertFalse(text.startsWith("\uFEFF"), "契约文件不得含 BOM: " + path);
        ContractFiles.MAPPER.readTree(text);
      }
    }
  }
}
