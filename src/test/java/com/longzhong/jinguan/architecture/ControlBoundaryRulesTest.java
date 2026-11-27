package com.longzhong.jinguan.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** 控制面边界门禁:投影只走服务只读 API;审计只增,清理仅保留任务。 */
class ControlBoundaryRulesTest {

  private static final Path MAIN = Path.of("src/main/java/com/longzhong/jinguan");
  private static final Path PROJECTION = MAIN.resolve("projection");
  private static final Path AUDIT = MAIN.resolve("audit");

  @Test
  void projectionNeverTouchesServiceDatabase() throws IOException {
    Pattern forbidden =
        Pattern.compile(
            "JdbcTemplate|javax\\.sql|java\\.sql\\.(Connection|DataSource)|jdbc:|DriverManager");
    List<String> violations =
        sources(PROJECTION).stream()
            .filter(path -> forbidden.matcher(read(path)).find())
            .map(Path::toString)
            .toList();
    assertTrue(violations.isEmpty(), "只读投影不得直连服务数据库: " + violations);
    // 投影模块唯一持久化对象是自建缓存表。
    String entity = read(PROJECTION.resolve("ServiceProjectionEntity.java"));
    assertTrue(entity.contains("service_projections"), "投影缓存表应独立于服务库");
  }

  @Test
  void auditRepositoryIsAppendOnly() throws IOException {
    String repository = read(AUDIT.resolve("ControlAuditRepository.java"));
    assertFalse(
        Pattern.compile("\\bdelete\\w*\\s*\\(", Pattern.CASE_INSENSITIVE)
            .matcher(repository)
            .find(),
        "审计仓储不得声明删除方法");
    assertFalse(
        Pattern.compile("\\bupdate\\w*\\s*\\(", Pattern.CASE_INSENSITIVE)
            .matcher(repository)
            .find(),
        "审计仓储不得声明更新方法");
  }

  @Test
  void onlyRetentionServiceDeletesAuditRows() throws IOException {
    Pattern auditDml =
        Pattern.compile(
            "(?i)\\b(delete\\s+from\\s+control_audit_log|update\\s+control_audit_log)\\b");
    List<String> violations =
        sources(AUDIT).stream()
            .filter(path -> !path.endsWith("AuditRetentionService.java"))
            .filter(path -> auditDml.matcher(read(path)).find())
            .map(Path::toString)
            .toList();
    assertTrue(violations.isEmpty(), "只有保留期任务可以删除/更新在线审计: " + violations);
    assertTrue(
        auditDml.matcher(read(AUDIT.resolve("AuditRetentionService.java"))).find(),
        "保留期任务应包含唯一审计清理路径");
  }

  private static List<Path> sources(Path directory) throws IOException {
    try (Stream<Path> paths = Files.walk(directory)) {
      return paths.filter(path -> path.toString().endsWith(".java")).toList();
    }
  }

  private static String read(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("读取源码失败: " + path, e);
    }
  }
}
