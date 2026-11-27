package com.longzhong.jinguan.serviceidentity;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 启用服务身份配置并在启动时做 fail-closed 校验。 */
@Configuration
@EnableConfigurationProperties(ServiceAuthProperties.class)
public class ServiceAuthConfig {

  public static final String MANAGED_MODE = "managed";
  public static final String LOCAL_MODE = "local";

  public ServiceAuthConfig(ServiceAuthProperties properties) {
    if (!MANAGED_MODE.equals(properties.mode()) && !LOCAL_MODE.equals(properties.mode())) {
      throw new IllegalStateException(
          "jinguan.service-auth.mode 只能是 local 或 managed: " + properties.mode());
    }
  }

  /** 目录中某服务可用的 scope 并集;未知服务返回空。 */
  public static List<String> scopesForService(ServiceAuthProperties properties, String serviceId) {
    return properties.clients().values().stream()
        .filter(client -> serviceId != null && serviceId.equals(client.serviceId()))
        .flatMap(client -> client.scopeList().stream())
        .distinct()
        .toList();
  }
}
