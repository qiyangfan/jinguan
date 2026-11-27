package com.longzhong.jinguan.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** 契约文件定位、schema 加载与敏感字段扫描(harness 共用)。 */
final class ContractFiles {

  static final Path ROOT = Path.of("src/main/resources/contracts/control-plane");
  static final ObjectMapper MAPPER = new ObjectMapper();
  private static final JsonSchemaFactory FACTORY =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
  private static final String PROPERTIES_KEY = "properties";
  private static final Pattern SENSITIVE =
      Pattern.compile(".*(password|passwd|secret|credential|authorization|connection|dsn).*");

  private ContractFiles() {}

  static JsonSchema schemaOf(String file) throws IOException {
    return FACTORY.getSchema(MAPPER.readTree(Files.readString(ROOT.resolve(file))));
  }

  static List<Path> schemaFiles() throws IOException {
    try (Stream<Path> paths = Files.walk(ROOT)) {
      return paths.filter(path -> path.toString().endsWith(".schema.json")).toList();
    }
  }

  static Set<String> schemaFilesOnDisk() throws IOException {
    Set<String> onDisk = new TreeSet<>();
    for (Path path : schemaFiles()) {
      onDisk.add(ROOT.relativize(path).toString());
    }
    return onDisk;
  }

  static List<String> sensitivePropertyViolations() throws IOException {
    List<String> violations = new ArrayList<>();
    for (Path path : schemaFiles()) {
      collect(MAPPER.readTree(Files.readString(path)), path, violations);
    }
    return violations;
  }

  static String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private static void collect(JsonNode node, Path file, List<String> violations) {
    if (node.isObject()) {
      node.fields()
          .forEachRemaining(
              entry -> {
                String name = entry.getKey();
                if (SENSITIVE.matcher(name.toLowerCase(Locale.ROOT)).matches()) {
                  violations.add(file.getFileName() + ":" + name);
                }
                if (PROPERTIES_KEY.equals(name)) {
                  entry
                      .getValue()
                      .fieldNames()
                      .forEachRemaining(prop -> checkProperty(prop, file, violations));
                }
                collect(entry.getValue(), file, violations);
              });
    } else if (node.isArray()) {
      node.forEach(child -> collect(child, file, violations));
    }
  }

  private static void checkProperty(String prop, Path file, List<String> violations) {
    String lower = prop.toLowerCase(Locale.ROOT);
    if (SENSITIVE.matcher(lower).matches() || "api_key".equals(lower) || "apikey".equals(lower)) {
      violations.add(file.getFileName() + ":" + prop);
    }
  }
}
