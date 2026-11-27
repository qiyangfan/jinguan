package com.longzhong.jinguan.projection;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 服务只读 API 访问配置:地址按 service_id 静态映射,不做服务发现。 */
@ConfigurationProperties(prefix = "jinguan.services.read")
public record ServiceReadProperties(
    Map<String, String> baseUrls,
    int staleAfterSeconds,
    int ttlSeconds,
    int retentionDays,
    int timeoutMillis,
    String localToken,
    Managed managed) {

  /** managed 模式下 jinguan 出站 client_credentials 配置。 */
  public record Managed(String tokenUri, Map<String, Client> clients) {

    public Managed {
      clients = clients == null ? Map.of() : Map.copyOf(clients);
    }
  }

  /** 出站 client 凭证:只从环境变量注入。 */
  public record Client(String clientId, String clientSecret) {}

  public ServiceReadProperties {
    baseUrls = baseUrls == null ? Map.of() : Map.copyOf(baseUrls);
    staleAfterSeconds = staleAfterSeconds > 0 ? staleAfterSeconds : 60;
    ttlSeconds = ttlSeconds > 0 ? ttlSeconds : 30;
    retentionDays = retentionDays > 0 ? retentionDays : 7;
    timeoutMillis = timeoutMillis > 0 ? timeoutMillis : 3000;
    managed = managed == null ? new Managed("", Map.of()) : managed;
  }

  public String baseUrlOf(String serviceId) {
    String url = baseUrls.get(serviceId);
    return url == null || url.isBlank() ? null : url.replaceAll("/+$", "");
  }
}
