package com.longzhong.jinguan.serviceidentity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/** managed 模式:校验 Keycloak 短时 token(issuer/JWKS/azp/scope)。 */
@Component
class ManagedServiceAuthenticator {

  private static final Logger log = LoggerFactory.getLogger(ManagedServiceAuthenticator.class);

  private final ServiceAuthProperties properties;
  private final ServiceAuthRejector rejector;
  private final JwtDecoder decoder;

  ManagedServiceAuthenticator(ServiceAuthProperties properties, ServiceAuthRejector rejector) {
    this.properties = properties;
    this.rejector = rejector;
    this.decoder = buildDecoder(properties);
  }

  @SuppressWarnings("PMD.PreserveStackTrace") // 对外只暴露认证结论;原因已写日志,不回传验签细节
  ServicePrincipal authenticate(String token, HttpServletRequest request, String requiredScope) {
    if (decoder == null) {
      throw rejector.reject(null, requiredScope, request, "managed 模式缺少 JWKS 配置", true);
    }
    Jwt jwt;
    try {
      jwt = decoder.decode(token);
    } catch (JwtException e) {
      log.warn("服务 token 验签失败: {}", e.getMessage());
      throw rejector.reject(null, requiredScope, request, "服务 token 验签失败", true);
    }
    String clientId = clientIdOf(jwt);
    ServiceAuthProperties.Client entry = requireEnabledClient(clientId, requiredScope, request);
    List<String> scopes = entry.scopeList();
    rejector.requireScope(entry.serviceId(), scopes, requiredScope, request);
    return new ServicePrincipal(entry.serviceId(), clientId, scopes);
  }

  private static String clientIdOf(Jwt jwt) {
    return Optional.ofNullable(jwt.getClaimAsString("azp"))
        .orElseGet(() -> jwt.getClaimAsString("client_id"));
  }

  private ServiceAuthProperties.Client requireEnabledClient(
      String clientId, String requiredScope, HttpServletRequest request) {
    ServiceAuthProperties.Client entry =
        clientId == null ? null : properties.clients().get(clientId);
    if (entry == null) {
      throw rejector.reject(clientId, requiredScope, request, "未知服务 client", true);
    }
    if (entry.disabled()) {
      throw rejector.reject(entry.serviceId(), requiredScope, request, "服务身份已吊销", true);
    }
    return entry;
  }

  private static JwtDecoder buildDecoder(ServiceAuthProperties properties) {
    if (!ServiceAuthConfig.MANAGED_MODE.equals(properties.mode())) {
      return null;
    }
    String issuer = properties.managed().issuer();
    String jwkSetUri = properties.managed().jwkSetUri();
    if (issuer == null || issuer.isBlank() || jwkSetUri == null || jwkSetUri.isBlank()) {
      throw new IllegalStateException(
          "managed 模式必须配置 jinguan.service-auth.managed.issuer 与 jwk-set-uri");
    }
    NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    return jwtDecoder;
  }
}
