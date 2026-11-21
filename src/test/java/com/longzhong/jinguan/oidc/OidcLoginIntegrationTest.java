package com.longzhong.jinguan.oidc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.longzhong.jinguan.user.PrincipalRepository;
import com.longzhong.jinguan.user.TokenIssuer;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 虎符 OIDC RP:PKCE、state 单次、nonce/aud/iss 与 JWKS 验签的端到端测试。 */
@SpringBootTest
@SuppressWarnings({"PMD.ExcessiveImports", "PMD.TooManyMethods", "PMD.DoNotUseThreads"})
class OidcLoginIntegrationTest {

  private static final String NONCE_CLAIM = "nonce";
  private static final String CLIENT_ID = "jinguan-rp";
  private static final HttpServer IDP = startIdp();
  private static final RSAKey KEY = generateKey();
  private static final AtomicReference<String> NONCE = new AtomicReference<>(NONCE_CLAIM);
  private static final AtomicReference<String> AUDIENCE = new AtomicReference<>(CLIENT_ID);
  private static final AtomicReference<Instant> EXPIRES = new AtomicReference<>();
  private static final java.util.concurrent.atomic.AtomicInteger TOKEN_DELAY_MS =
      new java.util.concurrent.atomic.AtomicInteger();

  static {
    IDP.createContext("/.well-known/openid-configuration", exchange -> json(exchange, discovery()));
    IDP.createContext(
        "/jwks", exchange -> json(exchange, new JWKSet(KEY.toPublicJWK()).toJSONObject()));
    IDP.createContext(
        "/token",
        exchange -> {
          int delay = TOKEN_DELAY_MS.get();
          if (delay > 0) {
            try {
              Thread.sleep(delay);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
          json(exchange, Map.of("id_token", idToken()));
        });
    IDP.createContext(
        "/end_session",
        exchange -> {
          exchange.getResponseHeaders().add("Location", "https://rp.example/logged-out");
          try {
            exchange.sendResponseHeaders(302, -1);
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
          exchange.close();
        });
    IDP.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:jinguan-oidc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    registry.add("spring.datasource.username", () -> "sa");
    registry.add("spring.datasource.password", () -> "");
    registry.add("jinguan.oidc.enabled", () -> "true");
    registry.add("jinguan.oidc.issuer", OidcLoginIntegrationTest::issuer);
    registry.add("jinguan.oidc.client-id", () -> CLIENT_ID);
    registry.add("jinguan.oidc.client-secret", () -> "rp-secret");
    registry.add("jinguan.oidc.redirect-uri", () -> "https://rp.example/auth/oidc/callback");
    registry.add("jinguan.oidc.post-logout-redirect-uri", () -> "https://rp.example/login");
    registry.add("jinguan.oidc.frontend-success-url", () -> "/login?from=oidc");
  }

  @Autowired OidcLoginService login;
  @Autowired PrincipalRepository principals;
  @Autowired TokenIssuer tokens;
  @Autowired OidcLoginStateRepository states;
  @Autowired OidcStateCleanupScheduler cleanup;

  @BeforeAll
  static void reset() {
    NONCE.set(NONCE_CLAIM);
    AUDIENCE.set(CLIENT_ID);
  }

  @Test
  @SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
  void startUsesPkceS256AndCallbackCreatesPrincipal() {
    OidcLoginService.Start start = login.start("/wallet");
    URI uri = URI.create(start.location());
    String query = uri.getRawQuery();
    assertTrue(query.contains("code_challenge_method=S256"), "必须使用 S256");
    assertTrue(query.contains("code_challenge="), "必须携带 PKCE challenge");
    assertTrue(query.contains("nonce="), "必须携带 nonce");
    assertFalse(query.contains("code_verifier"), "verifier 不得出现在浏览器地址");

    String nonce = queryValue(query, NONCE_CLAIM);
    NONCE.set(nonce);
    TokenIssuer.TokenPair pair = login.callback(start.state(), "code-1");
    assertNotNull(pair.access(), "必须签发 access");
    assertNotNull(pair.refresh(), "必须签发 refresh");
    assertTrue(
        principals.findById("01900000-0000-7000-8000-000000000123").isPresent(), "首登必须建 principal");
  }

  @Test
  void returnToIsPreservedForSafeLocalPath() {
    OidcLoginService.Start start = login.start("/wallet");
    NONCE.set(queryValue(URI.create(start.location()).getRawQuery(), NONCE_CLAIM));
    login.callback(start.state(), "code-return");
    org.junit.jupiter.api.Assertions.assertEquals(
        "/wallet", login.returnTo(start.state()), "安全本地回跳必须原样保留");
  }

  @Test
  void stateIsSingleUse() {
    OidcLoginService.Start start = login.start(null);
    NONCE.set(queryValue(URI.create(start.location()).getRawQuery(), NONCE_CLAIM));
    login.callback(start.state(), "code-2");
    assertThrows(IllegalArgumentException.class, () -> login.callback(start.state(), "code-2"));
  }

  @Test
  void expiredStatesArePurged() {
    states.save(
        new OidcLoginState(
            "expired-hash", NONCE_CLAIM, "verifier", null, Instant.now().minusSeconds(1)));
    OidcLoginState fresh =
        new OidcLoginState(
            "fresh-hash", NONCE_CLAIM, "verifier", null, Instant.now().plusSeconds(600));
    states.save(fresh);

    cleanup.purgeExpiredStates();

    assertTrue(states.findById("expired-hash").isEmpty(), "过期 state 必须被清理");
    assertTrue(states.findById("fresh-hash").isPresent(), "未过期 state 不得误删");
  }

  @Test
  void nonceAndAudienceMismatchRejected() {
    OidcLoginService.Start start = login.start(null);
    NONCE.set(queryValue(URI.create(start.location()).getRawQuery(), NONCE_CLAIM));
    NONCE.set("wrong-nonce");
    assertThrows(IllegalArgumentException.class, () -> login.callback(start.state(), "code-3"));

    OidcLoginService.Start start2 = login.start(null);
    NONCE.set(queryValue(URI.create(start2.location()).getRawQuery(), NONCE_CLAIM));
    AUDIENCE.set("other-client");
    assertThrows(IllegalArgumentException.class, () -> login.callback(start2.state(), "code-4"));
    AUDIENCE.set(CLIENT_ID);
  }

  @Test
  void tokenEndpointTimeoutFailsFast() {
    OidcLoginService.Start start = login.start(null);
    NONCE.set(queryValue(URI.create(start.location()).getRawQuery(), NONCE_CLAIM));
    TOKEN_DELAY_MS.set(6000);
    long begin = System.nanoTime();
    assertThrows(RuntimeException.class, () -> login.callback(start.state(), "code-timeout"));
    long elapsedMs = (System.nanoTime() - begin) / 1_000_000;
    TOKEN_DELAY_MS.set(0);
    assertTrue(elapsedMs < 8_000, "token 端点必须按读超时失败,实际耗时 " + elapsedMs + "ms");
  }

  @Test
  void expiredIdTokenRejected() {
    OidcLoginService.Start start = login.start(null);
    NONCE.set(queryValue(URI.create(start.location()).getRawQuery(), NONCE_CLAIM));
    EXPIRES.set(Instant.now().minusSeconds(3600));
    assertThrows(IllegalArgumentException.class, () -> login.callback(start.state(), "code-5"));
    EXPIRES.set(null);
  }

  @Test
  void endSessionUsesRegisteredRedirectWithoutTokens() {
    String target = login.endSessionLocation();
    assertNotNull(target, "end_session 地址必须存在");
    assertTrue(target.contains("client_id=" + CLIENT_ID), "必须携带 client_id");
    assertTrue(target.contains("post_logout_redirect_uri="), "必须携带登记回跳");
    assertFalse(target.contains("id_token"), "不得携带令牌");
  }

  private static String issuer() {
    return "http://"
        + java.net.InetAddress.getLoopbackAddress().getHostAddress()
        + ":"
        + IDP.getAddress().getPort();
  }

  private static Map<String, Object> discovery() {
    return Map.of(
        "issuer", issuer(),
        "authorization_endpoint", issuer() + "/authorize",
        "token_endpoint", issuer() + "/token",
        "jwks_uri", issuer() + "/jwks",
        "end_session_endpoint", issuer() + "/end_session");
  }

  private static String idToken() {
    try {
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .issuer(issuer())
              .audience(AUDIENCE.get())
              .subject("01900000-0000-7000-8000-000000000123")
              .claim(NONCE_CLAIM, NONCE.get())
              .claim("preferred_username", "alice")
              .issueTime(Date.from(Instant.now().minusSeconds(5)))
              .expirationTime(
                  Date.from(EXPIRES.get() == null ? Instant.now().plusSeconds(300) : EXPIRES.get()))
              .build();
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256)
                  .keyID(KEY.getKeyID())
                  .type(JOSEObjectType.JWT)
                  .build(),
              claims);
      jwt.sign(new RSASSASigner(KEY));
      return jwt.serialize();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static HttpServer startIdp() {
    try {
      return HttpServer.create(
          new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static RSAKey generateKey() {
    try {
      return new RSAKeyGenerator(2048).keyID("test-key").generate();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void json(com.sun.net.httpserver.HttpExchange exchange, Object body) {
    try {
      byte[] raw = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(body);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, raw.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(raw);
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String queryValue(String query, String key) {
    for (String part : query.split("&")) {
      if (part.startsWith(key + "=")) {
        return java.net.URLDecoder.decode(part.substring(key.length() + 1), StandardCharsets.UTF_8);
      }
    }
    throw new IllegalArgumentException("missing " + key);
  }
}
