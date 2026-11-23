package com.longzhong.jinguan.oidc;

import java.util.Collection;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;

/** Keycloak client roles 白名单映射:仅本 client 的 jinguan-admin 授 ADMIN,其余一律 USER。 */
final class OidcRoleMapping {

  static final String ADMIN_CLIENT_ROLE = "jinguan-admin";
  static final String ROLE_ADMIN = "ADMIN";
  static final String ROLE_USER = "USER";

  private OidcRoleMapping() {}

  /** 只读取 resource_access[clientId].roles;realm_access、其它 client 与未知角色一律忽略。 */
  static String from(Jwt jwt, String clientId) {
    if (!(jwt.getClaim("resource_access") instanceof Map<?, ?> clients)) {
      return ROLE_USER;
    }
    if (!(clients.get(clientId) instanceof Map<?, ?> client)) {
      return ROLE_USER;
    }
    if (!(client.get("roles") instanceof Collection<?> roles)) {
      return ROLE_USER;
    }
    return roles.contains(ADMIN_CLIENT_ROLE) ? ROLE_ADMIN : ROLE_USER;
  }
}
