package com.longzhong.jinguan.oidc;

import com.longzhong.jinguan.user.PrincipalEntity;
import com.longzhong.jinguan.user.PrincipalService;
import com.longzhong.jinguan.user.TokenIssuer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/** Keycloak授权码 + PKCE 登录：state 单次、nonce/iss/aud/exp 校验与 JWKS 验签。 */
@Service
@SuppressWarnings({
  "PMD.CouplingBetweenObjects",
  "PMD.GodClass",
  "PMD.TooManyMethods"
}) // RP 协议编排集中在本类,拆分反而分散安全校验
public class OidcLoginService {

  private static final Duration STATE_TTL = Duration.ofMinutes(10);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final OidcProperties props;
  private final OidcDiscovery discovery;
  private final OidcLoginStateRepository states;
  private final PrincipalService principals;
  private final TokenIssuer tokens;
  private final RestClient http = buildHttpClient();
  private final Map<String, JwtDecoder> decoders = new ConcurrentHashMap<>();

  public OidcLoginService(
      OidcProperties props,
      OidcDiscovery discovery,
      OidcLoginStateRepository states,
      PrincipalService principals,
      TokenIssuer tokens) {
    if (props.enabled()
        && (blank(props.issuer())
            || blank(props.clientId())
            || blank(props.clientSecret())
            || blank(props.redirectUri()))) {
      throw new IllegalStateException(
          "启用Keycloak OIDC 时必须配置 issuer/client-id/client-secret/redirect-uri");
    }
    this.props = props;
    this.discovery = discovery;
    this.states = states;
    this.principals = principals;
    this.tokens = tokens;
  }

  /** 授权跳转地址与本次 state。 */
  public record Start(String location, String state) {}

  /** 生成一次性 state、nonce 与 PKCE verifier 并返回Keycloak授权地址。 */
  @Transactional
  public Start start(String returnTo) {
    requireEnabled();
    String state = randomToken();
    String nonce = randomToken();
    String verifier = randomToken();
    states.save(
        new OidcLoginState(
            sha256(state), nonce, verifier, safeReturnTo(returnTo), Instant.now().plus(STATE_TTL)));
    OidcDiscovery.Document document = discovery.get(props.issuer());
    String location =
        UriComponentsBuilder.fromUriString(document.authorizationEndpoint())
            .queryParam("response_type", "code")
            .queryParam("client_id", props.clientId())
            .queryParam("redirect_uri", props.redirectUri())
            .queryParam("scope", String.join(" ", props.scopes()))
            .queryParam("state", state)
            .queryParam("nonce", nonce)
            .queryParam("code_challenge", s256(verifier))
            .queryParam("code_challenge_method", "S256")
            .encode()
            .build()
            .toUriString();
    return new Start(location, state);
  }

  /** 原子消费 state、交换 code、验签 id_token 后 JIT 建档并签发本地双 token。 */
  @Transactional
  @SuppressWarnings("PMD.CyclomaticComplexity") // 单次回调的校验顺序不可拆散
  public TokenIssuer.TokenPair callback(String state, String code) {
    requireEnabled();
    if (blank(state) || blank(code) || states.consume(sha256(state), Instant.now()) != 1) {
      throw new IllegalArgumentException("state 无效或已使用");
    }
    OidcLoginState loginState =
        states.findById(sha256(state)).orElseThrow(() -> new IllegalArgumentException("state 无效"));
    OidcDiscovery.Document document = discovery.get(props.issuer());
    String idToken = exchangeCode(document.tokenEndpoint(), code, loginState.getCodeVerifier());
    Jwt jwt;
    try {
      jwt = decoder(document.jwksUri()).decode(idToken);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("id_token 无效", e);
    }
    if (!loginState.getNonce().equals(jwt.getClaimAsString("nonce"))) {
      throw new IllegalArgumentException("nonce 不匹配");
    }
    String subject = jwt.getSubject();
    if (blank(subject)) {
      throw new IllegalArgumentException("id_token 缺少 sub");
    }
    PrincipalEntity principal =
        principals.ensure(
            subject, preferredUsername(jwt), OidcRoleMapping.from(jwt, props.clientId()));
    return tokens.issue(principal);
  }

  /** 读取本次 state 登记的安全回跳路径;不存在/非法返回 null。 */
  public String returnTo(String state) {
    if (blank(state)) {
      return null;
    }
    return states.findById(sha256(state)).map(OidcLoginState::getReturnTo).orElse(null);
  }

  /** 本地登出后的Keycloak跳转地址；发现不可用或缺端点时返回 null。 */
  public String endSessionLocation() {
    if (!props.enabled()) {
      return null;
    }
    try {
      String endpoint = discovery.get(props.issuer()).endSessionEndpoint();
      if (blank(endpoint)) {
        return null;
      }
      UriComponentsBuilder builder =
          UriComponentsBuilder.fromUriString(endpoint).queryParam("client_id", props.clientId());
      if (!blank(props.postLogoutRedirectUri())) {
        builder.queryParam("post_logout_redirect_uri", props.postLogoutRedirectUri());
      }
      return builder.build().toUriString();
    } catch (RuntimeException e) {
      return null;
    }
  }

  public boolean enabled() {
    return props.enabled();
  }

  public String frontendSuccessUrl() {
    return props.frontendSuccessUrl();
  }

  private static RestClient buildHttpClient() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000);
    factory.setReadTimeout(5000);
    return RestClient.builder().requestFactory(factory).build();
  }

  private String exchangeCode(String tokenEndpoint, String code, String verifier) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("code", code);
    form.add("redirect_uri", props.redirectUri());
    form.add("code_verifier", verifier);
    @SuppressWarnings("unchecked")
    Map<String, Object> body =
        http.post()
            .uri(tokenEndpoint)
            .headers(
                headers -> {
                  headers.setBasicAuth(props.clientId(), props.clientSecret());
                  headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                })
            .body(form)
            .retrieve()
            .body(Map.class);
    Object idToken = body == null ? null : body.get("id_token");
    if (idToken == null || idToken.toString().isBlank()) {
      throw new IllegalArgumentException("token 响应缺少 id_token");
    }
    return idToken.toString();
  }

  private JwtDecoder decoder(String jwksUri) {
    return decoders.computeIfAbsent(jwksUri, this::buildDecoder);
  }

  private JwtDecoder buildDecoder(String jwksUri) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
    OAuth2TokenValidator<Jwt> audience =
        token -> {
          List<String> audiences = token.getAudience();
          return audiences != null && audiences.contains(props.clientId())
              ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(
                  new OAuth2Error("invalid_token", "audience mismatch", null));
        };
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(props.issuer()), audience));
    return decoder;
  }

  private void requireEnabled() {
    if (!props.enabled()) {
      throw new IllegalStateException("Keycloak OIDC 未启用");
    }
  }

  private static String preferredUsername(Jwt jwt) {
    String username = jwt.getClaimAsString("preferred_username");
    if (blank(username)) {
      username = jwt.getClaimAsString("name");
    }
    if (blank(username)) {
      String subject = jwt.getSubject();
      username =
          subject == null ? "user" : "user-" + subject.substring(0, Math.min(8, subject.length()));
    }
    return username;
  }

  private static String safeReturnTo(String returnTo) {
    if (returnTo == null
        || returnTo.isBlank()
        || !returnTo.startsWith("/")
        || returnTo.startsWith("//")) {
      return null;
    }
    return returnTo.length() > 512 ? null : returnTo;
  }

  private static String randomToken() {
    byte[] raw = new byte[32];
    RANDOM.nextBytes(raw);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }

  private static String s256(String verifier) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256Bytes(verifier));
  }

  static String sha256(String value) {
    return java.util.HexFormat.of().formatHex(sha256Bytes(value));
  }

  private static byte[] sha256Bytes(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  /** 供测试与审计读取配置的只读视图。 */
  public List<String> scopes() {
    return props.scopes();
  }
}
