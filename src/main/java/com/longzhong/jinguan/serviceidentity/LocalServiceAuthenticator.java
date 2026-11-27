package com.longzhong.jinguan.serviceidentity;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.stereotype.Component;

/** local 模式:pre-shared token 仅 loopback;service_id 必须已在目录登记。 */
@Component
class LocalServiceAuthenticator {

  private final ServiceAuthProperties properties;
  private final ServiceAuthRejector rejector;

  LocalServiceAuthenticator(ServiceAuthProperties properties, ServiceAuthRejector rejector) {
    this.properties = properties;
    this.rejector = rejector;
  }

  ServicePrincipal authenticate(String token, HttpServletRequest request, String requiredScope) {
    String serviceId = request.getHeader(ServiceAuthService.SERVICE_HEADER);
    if (!isLoopback(request.getRemoteAddr())) {
      throw rejector.reject(
          serviceId, requiredScope, request, "pre-shared 服务凭据仅允许 localhost", false);
    }
    if (!matchesAny(token)) {
      throw rejector.reject(serviceId, requiredScope, request, "服务 pre-shared 凭据无效", true);
    }
    requireServiceId(serviceId, requiredScope, request);
    List<String> scopes = ServiceAuthConfig.scopesForService(properties, serviceId);
    if (scopes.isEmpty()) {
      throw rejector.reject(serviceId, requiredScope, request, "服务不在身份目录", true);
    }
    rejector.requireScope(serviceId, scopes, requiredScope, request);
    return new ServicePrincipal(serviceId, "local:" + serviceId, scopes);
  }

  private void requireServiceId(
      String serviceId, String requiredScope, HttpServletRequest request) {
    if (serviceId == null || serviceId.isBlank()) {
      throw rejector.reject(
          null, requiredScope, request, "缺少 " + ServiceAuthService.SERVICE_HEADER, true);
    }
  }

  private boolean matchesAny(String token) {
    byte[] actual = token.getBytes(StandardCharsets.UTF_8);
    return matches(properties.localToken(), actual)
        || matches(properties.localTokenPrevious(), actual);
  }

  private static boolean matches(String expected, byte[] actual) {
    if (expected == null || expected.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual);
  }

  private static boolean isLoopback(String remoteAddr) {
    try {
      return InetAddress.getByName(remoteAddr).isLoopbackAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }
}
