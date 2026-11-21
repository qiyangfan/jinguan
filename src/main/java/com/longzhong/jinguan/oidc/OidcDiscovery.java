package com.longzhong.jinguan.oidc;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 发现文档短缓存；不可用时抛出异常，调用方回落到本地登录页。 */
@Component
@SuppressWarnings({
  "PMD.AvoidUsingVolatile",
  "PMD.AvoidSynchronizedAtMethodLevel",
  "PMD.ExceptionAsFlowControl"
}) // 发现缓存是尽力而为:volatile/同步仅保护一次刷新
public class OidcDiscovery {

  private static final Duration TTL = Duration.ofMinutes(5);

  record Document(
      String issuer,
      String authorizationEndpoint,
      String tokenEndpoint,
      String jwksUri,
      String endSessionEndpoint) {}

  private final RestClient http;
  private volatile Document cached;
  private volatile Instant fetchedAt = Instant.EPOCH;

  public OidcDiscovery() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000);
    factory.setReadTimeout(5000);
    this.http = RestClient.builder().requestFactory(factory).build();
  }

  Document get(String issuer) {
    Document current = cached;
    if (current != null && Instant.now().isBefore(fetchedAt.plus(TTL))) {
      return current;
    }
    return refresh(issuer);
  }

  private synchronized Document refresh(String issuer) {
    if (cached != null && Instant.now().isBefore(fetchedAt.plus(TTL))) {
      return cached;
    }
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> body =
          http.get().uri(issuer + "/.well-known/openid-configuration").retrieve().body(Map.class);
      if (body == null || !issuer.equals(body.get("issuer"))) {
        throw new RestClientException("discovery issuer mismatch");
      }
      Document document =
          new Document(
              issuer,
              required(body, "authorization_endpoint"),
              required(body, "token_endpoint"),
              required(body, "jwks_uri"),
              optional(body, "end_session_endpoint"));
      cached = document;
      fetchedAt = Instant.now();
      return document;
    } catch (RestClientException e) {
      throw new RestClientException("oidc discovery unavailable", e);
    }
  }

  private static String required(Map<String, Object> body, String key) {
    Object value = body.get(key);
    if (value == null || value.toString().isBlank()) {
      throw new RestClientException("discovery missing " + key);
    }
    return value.toString();
  }

  private static String optional(Map<String, Object> body, String key) {
    Object value = body.get(key);
    return value == null ? null : value.toString();
  }
}
