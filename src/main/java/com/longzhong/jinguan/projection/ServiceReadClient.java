package com.longzhong.jinguan.projection;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 服务只读 API 客户端:JDK HttpClient,带服务身份(managed client_credentials 或本地 pre-shared)。 */
@Component
public class ServiceReadClient {

  static final String PROJECTION_PATH = "/service/v1/read-projection";
  private static final int HTTP_OK = 200;

  private final ServiceReadProperties properties;
  private final ObjectMapper mapper;
  private final HttpClient client;
  private final OutboundTokenProvider tokenProvider;

  public ServiceReadClient(
      ServiceReadProperties properties, ObjectMapper mapper, OutboundTokenProvider tokenProvider) {
    this.properties = properties;
    this.mapper = mapper;
    this.tokenProvider = tokenProvider;
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.timeoutMillis()))
            .build();
  }

  /** 拉取原始投影;超时抛 java.net.http.HttpTimeoutException。 */
  public RawProjection fetch(String serviceId, String baseUrl)
      throws IOException, InterruptedException {
    String token = tokenProvider.tokenFor(serviceId);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + PROJECTION_PATH))
            .timeout(Duration.ofMillis(properties.timeoutMillis()))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .header("X-Jinguan-Service", serviceId)
            .GET()
            .build();
    HttpResponse<String> response =
        client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() != HTTP_OK) {
      throw new IOException("服务只读 API 返回 " + response.statusCode());
    }
    JsonNode body = mapper.readTree(response.body());
    String version = textOf(body, "version");
    String etag = textOf(body, "etag");
    String observedText = textOf(body, "observed_at");
    Instant observedAt =
        observedText == null || observedText.isBlank() ? null : Instant.parse(observedText);
    JsonNode sections = body.path("sections");
    return new RawProjection(
        version,
        etag,
        observedAt,
        mapper.writeValueAsString(sections.isMissingNode() ? mapper.createObjectNode() : sections));
  }

  private static String textOf(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asString();
  }

  /** 原始投影:版本/观察时间/脱敏前 sections JSON。 */
  public record RawProjection(
      String version, String etag, Instant observedAt, String sectionsJson) {}
}
