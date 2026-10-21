package com.longzhong.jinguan.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 运营价目以 credit 整数保存（1 credit = 本币 10⁻⁹）；时段窗口以 JSON 保存。 */
@Entity
@Table(name = "pricing_entries")
public class PricingEntryEntity {

  @Id
  @Column(nullable = false, length = 128)
  private String alias;

  @Column(nullable = false, length = 8)
  private String currency;

  @Column(name = "input_credits", nullable = false)
  private long inputCredits;

  @Column(name = "cached_input_credits", nullable = false)
  private long cachedInputCredits;

  @Column(name = "output_credits", nullable = false)
  private long outputCredits;

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
      long inputCredits,
      long cachedInputCredits,
      long outputCredits,
      String timezone,
      String timeWindowsJson) {
    this.alias = alias;
    this.currency = currency;
    this.inputCredits = inputCredits;
    this.cachedInputCredits = cachedInputCredits;
    this.outputCredits = outputCredits;
    this.timezone = timezone;
    this.timeWindowsJson = timeWindowsJson;
    this.updatedAt = Instant.now();
  }

  public static PricingEntryEntity create(
      String alias,
      String currency,
      long inputCredits,
      long cachedInputCredits,
      long outputCredits,
      String timezone,
      String timeWindowsJson) {
    return new PricingEntryEntity(
        alias,
        currency,
        inputCredits,
        cachedInputCredits,
        outputCredits,
        timezone,
        timeWindowsJson);
  }

  /** 替换价目内容并刷新更新时间。 */
  public void update(
      String currency,
      long inputCredits,
      long cachedInputCredits,
      long outputCredits,
      String timezone,
      String timeWindowsJson) {
    this.currency = currency;
    this.inputCredits = inputCredits;
    this.cachedInputCredits = cachedInputCredits;
    this.outputCredits = outputCredits;
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

  public long getInputCredits() {
    return inputCredits;
  }

  public long getCachedInputCredits() {
    return cachedInputCredits;
  }

  public long getOutputCredits() {
    return outputCredits;
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
