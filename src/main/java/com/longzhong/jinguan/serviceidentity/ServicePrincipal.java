package com.longzhong.jinguan.serviceidentity;

import java.util.List;

/** 通过校验的服务调用方身份;能力边界由目录 scope 决定。 */
public record ServicePrincipal(String serviceId, String clientId, List<String> scopes) {

  public ServicePrincipal {
    scopes = List.copyOf(scopes);
  }
}
