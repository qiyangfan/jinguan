package com.longzhong.jinguan.pricing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.longzhong.jinguan.common.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 管理渠道价目并生成与 muniu 对齐的下发负载。 */
@Service
public class PricingService {

  private static final Pattern MULTIPLIER = Pattern.compile("^\\d+(\\.\\d{1,4})?$");

  private final ObjectMapper mapper = new ObjectMapper();
  private final PricingEntryRepository entries;

  public PricingService(PricingEntryRepository entries) {
    this.entries = entries;
  }

  public record TimeWindow(String window, String multiplier) {}

  /** 与 muniu PricingView 对齐的下发格式。 */
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
      long inputCredits,
      long cachedInputCredits,
      long outputCredits,
      String timezone,
      List<TimeWindow> timeWindows) {}

  /** muniu 价目下发响应。 */
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
                    cmd.inputCredits(),
                    cmd.cachedInputCredits(),
                    cmd.outputCredits(),
                    cmd.timezone(),
                    windowsJson),
            () ->
                entries.save(
                    PricingEntryEntity.create(
                        alias,
                        cmd.currency(),
                        cmd.inputCredits(),
                        cmd.cachedInputCredits(),
                        cmd.outputCredits(),
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
        creditsToDecimal(e.getInputCredits()),
        creditsToDecimal(e.getCachedInputCredits()),
        creditsToDecimal(e.getOutputCredits()),
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
    } catch (JacksonException e) {
      throw new IllegalArgumentException("time_windows 序列化失败", e);
    }
  }

  private List<TimeWindow> parseWindows(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return List.of(mapper.readValue(json, TimeWindow[].class));
    } catch (JacksonException e) {
      throw new IllegalArgumentException("time_windows 数据损坏: " + json, e);
    }
  }

  /** 将 credit 转为最多 9 位小数的十进制字符串。 */
  static String creditsToDecimal(long credits) {
    if (credits == 0) {
      return "0";
    }
    return BigDecimal.valueOf(credits, 9).stripTrailingZeros().toPlainString();
  }
}
