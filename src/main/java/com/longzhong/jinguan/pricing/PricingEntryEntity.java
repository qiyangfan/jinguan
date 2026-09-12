package com.longzhong.jinguan.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 渠道价目(运营态):micros 整数存储(与 muniu InputMicro 同义),time_windows 以 最终下发形态的 JSON 数组字符串存储(multiplier 为 ≤4
 * 位小数去尾零字符串)。
 */
@Entity
@Table(name = "pricing_entries")
public class PricingEntryEntity {

  @Id
  @Column(nullable = false, length = 128)
  private String alias;

  @Column(nullable = false, length = 8)
  private String currency;

  @Column(name = "input_micros", nullable = false)
  private long inputMicros;

  @Column(name = "cached_input_micros", nullable = false)
  private long cachedInputMicros;

  @Column(name = "output_micros", nullable = false)
  private long outputMicros;

  @Column(nullable = true, length = 64)
  private String timezone;

  @Column(name = "time_windows_json", nullable = true, columnDefinition = "TEXT")
  private String timeWindowsJson;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PricingEntryEntity() {} // JPA

  private PricingEntryEntity(
      String alias,
      String currency,
      long inputMicros,
      long cachedInputMicros,
      long outputMicros,
      String timezone,
      String timeWindowsJson) {
    this.alias = alias;
    this.currency = currency;
    this.inputMicros = inputMicros;
    this.cachedInputMicros = cachedInputMicros;
    this.outputMicros = outputMicros;
    this.timezone = timezone;
    this.timeWindowsJson = timeWindowsJson;
    this.updatedAt = Instant.now();
  }

  public static PricingEntryEntity create(
      String alias,
      String currency,
      long inputMicros,
      long cachedInputMicros,
      long outputMicros,
      String timezone,
      String timeWindowsJson) {
    return new PricingEntryEntity(
        alias, currency, inputMicros, cachedInputMicros, outputMicros, timezone, timeWindowsJson);
  }

  /** 换价(意图方法):整体替换价目内容并刷新 updated_at。 */
  public void update(
      String currency,
      long inputMicros,
      long cachedInputMicros,
      long outputMicros,
      String timezone,
      String timeWindowsJson) {
    this.currency = currency;
    this.inputMicros = inputMicros;
    this.cachedInputMicros = cachedInputMicros;
    this.outputMicros = outputMicros;
    this.timezone = timezone;
    this.timeWindowsJson = timeWindowsJson;
    this.updatedAt = Instant.now();
  }

  public String getAlias() {
    return alias;
  }

  public String getCurrency() {
    return currency;
  }

  public long getInputMicros() {
    return inputMicros;
  }

  public long getCachedInputMicros() {
    return cachedInputMicros;
  }

  public long getOutputMicros() {
    return outputMicros;
  }

  public String getTimezone() {
    return timezone;
  }

  public String getTimeWindowsJson() {
    return timeWindowsJson;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
