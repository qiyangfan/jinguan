package com.longzhong.jinguan.serviceidentity;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 服务身份目录:machine client → service/scope/environment;Keycloak 是身份权威,本仓只做目录与校验。 */
@ConfigurationProperties(prefix = "jinguan.service-auth")
public record ServiceAuthProperties(
    String mode,
    String localToken,
    String localTokenPrevious,
    Managed managed,
    Map<String, Client> clients) {

  /** managed 模式的 Keycloak 校验参数。 */
  public record Managed(String issuer, String jwkSetUri) {}

  /** 单个 machine client 的目录项。 */
  public record Client(String serviceId, String scopes, String environment, boolean disabled) {

    public List<String> scopeList() {
      return scopes == null || scopes.isBlank()
          ? List.of()
          : java.util.Arrays.stream(scopes.split(","))
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .toList();
    }
  }

  public ServiceAuthProperties {
    mode = mode == null || mode.isBlank() ? "local" : mode.trim().toLowerCase(Locale.ROOT);
    managed = managed == null ? new Managed("", "") : managed;
    clients = clients == null ? Map.of() : Map.copyOf(clients);
  }
}
