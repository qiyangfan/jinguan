package com.longzhong.jinguan.alerts;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 启用告警配置并校验 webhook allowlist(配置了目标就必须有白名单)。 */
@Configuration
@EnableConfigurationProperties(AlertProperties.class)
public class AlertsConfig {

  public AlertsConfig(AlertProperties properties) {
    if (properties.webhookConfigured() && properties.webhookAllowlist().isEmpty()) {
      throw new IllegalStateException("配置 jinguan.alerts.webhook-url 时必须提供 webhook-allowlist");
    }
    if (properties.webhookConfigured()
        && (properties.webhookSecret() == null || properties.webhookSecret().isBlank())) {
      throw new IllegalStateException("配置 webhook 时必须提供 webhook-secret");
    }
  }
}
