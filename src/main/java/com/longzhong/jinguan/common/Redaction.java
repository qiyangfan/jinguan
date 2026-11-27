package com.longzhong.jinguan.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 敏感数据脱敏:审计摘要与告警载荷共用的递归白名单占位。 */
public final class Redaction {

  /** 键命中即整体替换,防止凭据/连接串落入审计或告警。 */
  private static final Pattern SENSITIVE_KEY =
      Pattern.compile(
          ".*(password|passwd|token|secret|credential|api[_-]?key|authorization|connection|dsn).*",
          Pattern.CASE_INSENSITIVE);

  public static final String MASK = "***";

  private Redaction() {}

  /** 返回脱敏后的 Map 副本;嵌套 Map/List 递归处理。 */
  public static Map<String, Object> redact(Map<String, Object> source) {
    Map<String, Object> redacted = new LinkedHashMap<>();
    source.forEach((key, value) -> redacted.put(key, redactValue(key, value)));
    return redacted;
  }

  private static Object redactValue(String key, Object value) {
    if (SENSITIVE_KEY.matcher(key).matches()) {
      return MASK;
    }
    if (value instanceof Map<?, ?> nested) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) nested;
      return redact(map);
    }
    if (value instanceof List<?> list) {
      return list.stream().map(item -> redactValue(key, item)).toList();
    }
    return value;
  }
}
