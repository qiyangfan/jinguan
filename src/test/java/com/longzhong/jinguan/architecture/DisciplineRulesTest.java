package com.longzhong.jinguan.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** AGENTS.md 两条硬纪律的机器门禁：全库禁 public setter、ledger_entries 只追加。 */
class DisciplineRulesTest {

  private static final Path MAIN_SOURCES = Path.of("src/main/java/com/longzhong/jinguan");
  private static final String LEDGER_REPOSITORY = "LedgerEntryRepository.java";
  private static final String SETTER_VIOLATION = "public setter 违反工厂构造纪律: ";
  private static final String LEDGER_VIOLATION = "ledger 仅追加纪律被破坏: ";

  private static final Pattern PUBLIC_SETTER =
      Pattern.compile(
          "\\bpublic\\s+(?:static\\s+)?(?:final\\s+)?[\\w$.<>\\[\\], ?]+\\s+set[A-Z]\\w*\\s*\\(");
  private static final Pattern LEDGER_DML =
      Pattern.compile(
          "\\b(?:update|delete\\s+from)\\s+(?:ledger_entries|ledgerentryentity)\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern DELETE_METHOD = Pattern.compile("\\bdelete\\w*\\s*\\(");

  @Test
  void noPublicSettersInMainSources() throws IOException {
    List<String> violations =
        mainSources()
            .filter(path -> PUBLIC_SETTER.matcher(stripComments(read(path))).find())
            .map(Path::toString)
            .toList();
    assertTrue(violations.isEmpty(), SETTER_VIOLATION + violations);
  }

  @Test
  void ledgerStaysAppendOnly() throws IOException {
    List<String> violations = mainSources().flatMap(DisciplineRulesTest::ledgerViolations).toList();
    assertTrue(violations.isEmpty(), LEDGER_VIOLATION + violations);
  }

  private static Stream<String> ledgerViolations(Path path) {
    String text = stripComments(read(path));
    Stream.Builder<String> found = Stream.builder();
    if (text.contains("@Modifying") && text.contains("LedgerEntry")) {
      found.add(path + ": 账本持久化不得使用 @Modifying");
    }
    if (LEDGER_DML.matcher(text).find()) {
      found.add(path + ": 账本表不得出现 UPDATE/DELETE 语句");
    }
    Path fileName = path.getFileName();
    if (fileName != null && LEDGER_REPOSITORY.equals(fileName.toString())) {
      if (DELETE_METHOD.matcher(text).find()) {
        found.add(path + ": 账本仓储不得声明 delete 方法");
      }
      if (!text.contains("extends Repository<")) {
        found.add(path + ": 账本仓储必须收窄为 Repository,禁止回退到 JpaRepository");
      }
    }
    return found.build();
  }

  private static List<Path> mainSourcesList() throws IOException {
    try (Stream<Path> paths = Files.walk(MAIN_SOURCES)) {
      return paths.filter(path -> path.toString().endsWith(".java")).toList();
    }
  }

  private static Stream<Path> mainSources() throws IOException {
    return mainSourcesList().stream();
  }

  private static String read(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("读取源码失败: " + path, e);
    }
  }

  /** 去掉块注释与行注释，避免注释里的示例文本被误判。 */
  private static String stripComments(String source) {
    return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
  }
}
