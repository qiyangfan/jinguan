package com.longzhong.jinguan.alerts;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Webhook 通道:目标必须命中 allowlist,载荷 HMAC 签名,失败按异常上抛给重试。 */
@Component
public class WebhookAlertChannel {

  private static final int HTTP_SUCCESS_CLASS = 2;

  private final AlertProperties properties;
  private final ObjectMapper mapper;
  private final HttpClient client;

  public WebhookAlertChannel(AlertProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    if (properties.webhookConfigured()) {
      String host = URI.create(properties.webhookUrl()).getHost();
      if (host == null || !properties.webhookAllowlist().contains(host)) {
        throw new IllegalStateException("webhook 目标不在 allowlist: " + host);
      }
    }
  }

  public boolean enabled() {
    return properties.webhookConfigured();
  }

  void deliver(AlertEventEntity event) throws IOException, InterruptedException {
    byte[] body = mapper.writeValueAsString(bodyOf(event)).getBytes(StandardCharsets.UTF_8);
    String timestamp = Long.toString(Instant.now().getEpochSecond());
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(properties.webhookUrl()))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .header("X-Jinguan-Timestamp", timestamp)
            .header("X-Jinguan-Signature", "sha256=" + sign(timestamp, body))
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
    HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
    if (response.statusCode() / 100 != HTTP_SUCCESS_CLASS) {
      throw new IOException("webhook 返回 " + response.statusCode());
    }
  }

  private String sign(String timestamp, byte[] body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(mac.doFinal(body));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("webhook 签名失败", e);
    }
  }

  private Map<String, Object> bodyOf(AlertEventEntity event) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", event.getId());
    body.put("severity", event.getSeverity());
    body.put("source", event.getSource());
    body.put("type", event.getType());
    body.put("tenant", event.getTenantKey());
    body.put("service", event.getServiceId());
    body.put("observed_at", event.getObservedAt().toString());
    body.put("dedup_key", event.getDedupKey());
    body.put("runbook", event.getRunbook());
    body.put("payload", mapper.readValue(event.getPayloadJson(), Map.class));
    return body;
  }
}
