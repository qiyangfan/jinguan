package com.longzhong.jinguan.alerts;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 告警投递配置;webhook 凭证只从环境变量注入,目标域名须在 allowlist 内。 */
@ConfigurationProperties(prefix = "jinguan.alerts")
public record AlertProperties(
    String webhookUrl,
    String webhookSecret,
    List<String> webhookAllowlist,
    boolean emailEnabled,
    List<String> emailTo,
    int dedupWindowSeconds,
    int maxAttempts,
    int deliveryIntervalSeconds,
    String operationalToken) {

  public AlertProperties {
    webhookAllowlist =
        List.copyOf(
            webhookAllowlist == null
                ? List.of()
                : webhookAllowlist.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
    emailTo =
        List.copyOf(
            emailTo == null
                ? List.of()
                : emailTo.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
    dedupWindowSeconds = dedupWindowSeconds > 0 ? dedupWindowSeconds : 900;
    maxAttempts = maxAttempts > 0 ? maxAttempts : 5;
    deliveryIntervalSeconds = deliveryIntervalSeconds > 0 ? deliveryIntervalSeconds : 30;
  }

  public boolean webhookConfigured() {
    return webhookUrl != null && !webhookUrl.isBlank();
  }

  public boolean emailConfigured() {
    return emailEnabled && !emailTo.isEmpty();
  }
}
