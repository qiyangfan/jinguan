package com.longzhong.jinguan.projection;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 出站服务身份:本地模式用 pre-shared,managed 模式走 client_credentials 并缓存至过期前 30 秒; 未配置任何凭据时 fail-closed。 */
@Component
public class OutboundTokenProvider {

  private static final int EXPIRY_SKEW_SECONDS = 30;
  private static final int HTTP_OK = 200;
  private static final int MIN_CACHE_SECONDS = 30;

  private final ServiceReadProperties properties;
  private final ObjectMapper mapper;
  private final HttpClient client;
  private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

  public OutboundTokenProvider(ServiceReadProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  }

  public String tokenFor(String serviceId) {
    String localToken = properties.localToken();
    if (localToken != null && !localToken.isBlank()) {
      return localToken;
    }
    ServiceReadProperties.Client clientConfig = requireClient(serviceId);
    CachedToken cached = cache.get(serviceId);
    if (cached != null
        && cached.expiresAt().isAfter(Instant.now().plusSeconds(EXPIRY_SKEW_SECONDS))) {
      return cached.token();
    }
    CachedToken fresh = requestToken(serviceId, clientConfig);
    cache.put(serviceId, fresh);
    return fresh.token();
  }

  private ServiceReadProperties.Client requireClient(String serviceId) {
    var managed = properties.managed();
    var clientConfig = managed.clients().get(serviceId);
    if (managed.tokenUri() == null
        || managed.tokenUri().isBlank()
        || clientConfig == null
        || clientConfig.clientId() == null
        || clientConfig.clientSecret() == null) {
      throw new IllegalStateException("未配置服务只读出站凭据: " + serviceId);
    }
    return clientConfig;
  }

  @SuppressWarnings("PMD.DoNotUseThreads") // 中断标志必须恢复,由 JDK HttpClient 契约决定
  private CachedToken requestToken(String serviceId, ServiceReadProperties.Client clientConfig) {
    String form =
        "grant_type=client_credentials&client_id="
            + URLEncoder.encode(clientConfig.clientId(), StandardCharsets.UTF_8)
            + "&client_secret="
            + URLEncoder.encode(clientConfig.clientSecret(), StandardCharsets.UTF_8)
            + "&scope="
            + URLEncoder.encode("service:read", StandardCharsets.UTF_8);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(properties.managed().tokenUri()))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
            .build();
    try {
      HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return parseToken(serviceId, response);
    } catch (IOException e) {
      throw new IllegalStateException("client_credentials 调用失败: " + serviceId, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("client_credentials 调用被中断: " + serviceId, e);
    }
  }

  private CachedToken parseToken(String serviceId, HttpResponse<String> response) {
    if (response.statusCode() != HTTP_OK) {
      throw new IllegalStateException(
          "client_credentials 获取失败: " + serviceId + " -> " + response.statusCode());
    }
    JsonNode body = mapper.readTree(response.body());
    String token = body.path("access_token").asString();
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("client_credentials 响应缺少 access_token");
    }
    long expiresIn = Math.max(MIN_CACHE_SECONDS, body.path("expires_in").asLong(60));
    return new CachedToken(token, Instant.now().plusSeconds(expiresIn));
  }

  private record CachedToken(String token, Instant expiresAt) {}
}
