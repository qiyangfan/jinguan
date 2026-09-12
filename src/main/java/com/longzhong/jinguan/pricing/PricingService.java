package com.longzhong.jinguan.pricing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longzhong.jinguan.common.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 渠道与价目:运营态管理 + 面向 muniu 数据面的下发契约。 下发 JSON 形态与 muniu `admin.PricingView` 逐字段对齐(multiplier/input 为
 * 去尾零十进制字符串;time_windows 空数组 = 恒缺省价,合法形态)。
 */
@Service
public class PricingService {

  private static final Pattern MULTIPLIER = Pattern.compile("^\\d+(\\.\\d{1,4})?$");

  private final ObjectMapper mapper = new ObjectMapper();
  private final PricingEntryRepository entries;

  public PricingService(PricingEntryRepository entries) {
    this.entries = entries;
  }

  public record TimeWindow(String window, String multiplier) {}

  /** 下发契约形态(字段名 snake_case,与 muniu PricingView 一致)。 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PricingView(
      String alias,
      String source,
      String currency,
      @JsonProperty("input") String input,
      @JsonProperty("cached_input") String cachedInput,
      @JsonProperty("output") String output,
      String timezone,
      @JsonProperty("time_windows") List<TimeWindow> timeWindows,
      @JsonProperty("updated_at") Instant updatedAt) {}

  public record UpsertCommand(
      String currency,
      long inputMicros,
      long cachedInputMicros,
      long outputMicros,
      String timezone,
      List<TimeWindow> timeWindows) {}

  /** 契约端点负载:muniu 期待 {"pricing":[...]}。 */
  public record PricingEnvelope(List<PricingView> pricing) {}

  @Transactional
  public void upsert(String alias, UpsertCommand cmd) {
    validateMultiplier(cmd.timeWindows());
    String windowsJson = serialize(cmd.timeWindows());
    entries
        .findById(alias)
        .ifPresentOrElse(
            e ->
                e.update(
                    cmd.currency(),
                    cmd.inputMicros(),
                    cmd.cachedInputMicros(),
                    cmd.outputMicros(),
                    cmd.timezone(),
                    windowsJson),
            () ->
                entries.save(
                    PricingEntryEntity.create(
                        alias,
                        cmd.currency(),
                        cmd.inputMicros(),
                        cmd.cachedInputMicros(),
                        cmd.outputMicros(),
                        cmd.timezone(),
                        windowsJson)));
  }

  @Transactional
  public void delete(String alias) {
    if (!entries.existsById(alias)) {
      throw new NotFoundException("价目不存在: " + alias);
    }
    entries.deleteById(alias);
  }

  @Transactional(readOnly = true)
  public List<PricingView> list() {
    return entries.findAll().stream().map(this::toView).toList();
  }

  private PricingView toView(PricingEntryEntity e) {
    return new PricingView(
        e.getAlias(),
        "db",
        e.getCurrency(),
        microsToDecimal(e.getInputMicros()),
        microsToDecimal(e.getCachedInputMicros()),
        microsToDecimal(e.getOutputMicros()),
        e.getTimezone(),
        parseWindows(e.getTimeWindowsJson()),
        e.getUpdatedAt());
  }

  private static void validateMultiplier(List<TimeWindow> windows) {
    for (TimeWindow w : windows == null ? List.<TimeWindow>of() : windows) {
      if (w.window() == null
          || w.window().isBlank()
          || w.multiplier() == null
          || !MULTIPLIER.matcher(w.multiplier()).matches()) {
        throw new IllegalArgumentException("time_windows 非法: multiplier 须为 ≤4 位小数的正数字符串 — " + w);
      }
    }
  }

  private String serialize(List<TimeWindow> windows) {
    try {
      return mapper.writeValueAsString(windows == null ? List.of() : windows);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("time_windows 序列化失败", e);
    }
  }

  private List<TimeWindow> parseWindows(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return List.of(mapper.readValue(json, TimeWindow[].class));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("time_windows 数据损坏: " + json, e);
    }
  }

  /** μ → 去尾零十进制字符串(与 muniu FormatPrice 输出一致);0 → "0"。 */
  static String microsToDecimal(long micros) {
    if (micros == 0) {
      return "0";
    }
    return BigDecimal.valueOf(micros, 6).stripTrailingZeros().toPlainString();
  }
}
