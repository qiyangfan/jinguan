package com.longzhong.jinguan.ingest;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 多来源用量事件校验:来源口径(LLM 只认 muniu)、维度长度、时间不超前。 */
@Component
class UsageEventValidation {

  static final Pattern METRIC = Pattern.compile("[a-z][a-z0-9_.-]{1,63}");
  static final String SOURCE_MUNIU = "muniu";
  static final String SOURCE_KONGMING = "kongming";
  private static final Set<String> KINDS = Set.of("PRODUCT", "LLM");
  private static final String KIND_LLM = "LLM";
  private static final String LLM_PREFIX = "llm.";

  UsageEventService.Normalized normalize(String source, UsageEventService.EventInput input) {
    if (input == null) {
      throw new IllegalArgumentException("事件不能为空");
    }
    final String eventId = requireText(input.eventId(), "eventId", 128);
    String metric = requireText(input.metric(), "metric", 64);
    if (!METRIC.matcher(metric).matches()) {
      throw new IllegalArgumentException("metric 需匹配 [a-z][a-z0-9_.-]{1,63}");
    }
    String unit = requireText(input.unit(), "unit", 32);
    String kind = normalizeKind(source, input.kind());
    if (input.quantity() < 0) {
      throw new IllegalArgumentException("quantity 必须非负");
    }
    requireOccurredAt(input.occurredAt());
    return new UsageEventService.Normalized(
        eventId,
        optionalText(input.tenantKey(), 64, "tenantKey"),
        optionalText(input.subject(), 64, "subject"),
        optionalText(input.entitlementKey(), 128, "entitlementKey"),
        metric,
        input.quantity(),
        unit,
        kind,
        input.occurredAt());
  }

  private static String normalizeKind(String source, String kind) {
    String normalized =
        kind == null || kind.isBlank() ? "PRODUCT" : kind.trim().toUpperCase(Locale.ROOT);
    if (!KINDS.contains(normalized)) {
      throw new IllegalArgumentException("kind 只能是 PRODUCT/LLM");
    }
    if (KIND_LLM.equals(normalized) && !SOURCE_MUNIU.equals(source)) {
      throw new IllegalArgumentException("LLM token/费用只允许 muniu 上报");
    }
    return normalized;
  }

  static void rejectKongmingLlmMetric(String source, String metric) {
    if (SOURCE_KONGMING.equals(source) && metric.startsWith(LLM_PREFIX)) {
      throw new IllegalArgumentException("kongming 不得上报 LLM 指标,避免与 muniu 双计");
    }
  }

  private static void requireOccurredAt(Instant occurredAt) {
    if (occurredAt == null) {
      throw new IllegalArgumentException("occurredAt 必填");
    }
    if (occurredAt.isAfter(Instant.now().plusSeconds(600))) {
      throw new IllegalArgumentException("occurredAt 超前于当前时间过多");
    }
  }

  private static String requireText(String value, String field, int max) {
    if (value == null || value.isBlank() || value.length() > max) {
      throw new IllegalArgumentException(field + " 必填且不超过 " + max + " 字符");
    }
    return value.trim();
  }

  private static String optionalText(String value, int max, String field) {
    if (value == null || value.isBlank()) {
      return null;
    }
    if (value.length() > max) {
      throw new IllegalArgumentException(field + " 不超过 " + max + " 字符");
    }
    return value.trim();
  }
}
