package com.longzhong.jinguan.serviceidentity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 服务身份认证入口:managed 校验 Keycloak 签发的短时 token(issuer/JWKS/scope),local 仅接受 loopback 的 pre-shared
 * token;身份缺失或越权 fail-closed 并审计。
 */
@Service
public class ServiceAuthService {

  public static final String SERVICE_HEADER = "X-Service-Id";

  private final ServiceAuthProperties properties;
  private final ServiceAuthRejector rejector;
  private final LocalServiceAuthenticator local;
  private final ManagedServiceAuthenticator managed;

  public ServiceAuthService(
      ServiceAuthProperties properties,
      ServiceAuthRejector rejector,
      LocalServiceAuthenticator local,
      ManagedServiceAuthenticator managed) {
    this.properties = properties;
    this.rejector = rejector;
    this.local = local;
    this.managed = managed;
  }

  /** 认证并校验 scope;失败抛 401/403 且写审计与告警(去重)。 */
  public ServicePrincipal authenticate(HttpServletRequest request, String requiredScope) {
    String authorization = request.getHeader("Authorization");
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw rejector.reject(null, requiredScope, request, "缺少服务身份凭证", true);
    }
    String token = authorization.substring("Bearer ".length());
    return ServiceAuthConfig.LOCAL_MODE.equals(properties.mode())
        ? local.authenticate(token, request, requiredScope)
        : managed.authenticate(token, request, requiredScope);
  }

  /** 供控制台展示的目录项(不含任何密钥)。 */
  public List<DirectoryEntry> directory() {
    return properties.clients().entrySet().stream()
        .map(
            entry ->
                new DirectoryEntry(
                    entry.getKey(),
                    entry.getValue().serviceId(),
                    entry.getValue().scopeList(),
                    entry.getValue().environment() == null
                        ? "managed"
                        : entry.getValue().environment(),
                    entry.getValue().disabled()))
        .sorted(Comparator.comparing(DirectoryEntry::clientId))
        .toList();
  }

  /** 服务身份目录视图。 */
  public record DirectoryEntry(
      String clientId,
      String serviceId,
      List<String> scopes,
      String environment,
      boolean disabled) {

    /** 复制 scope 列表,避免外部可变集合泄漏。 */
    public DirectoryEntry {
      scopes = List.copyOf(scopes);
    }
  }
}
