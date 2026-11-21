package com.longzhong.jinguan.oidc;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 虎符 OIDC RP 配置；client secret 只从环境变量注入。 */
@ConfigurationProperties(prefix = "jinguan.oidc")
public record OidcProperties(
    boolean enabled,
    String issuer,
    String clientId,
    String clientSecret,
    String redirectUri,
    String postLogoutRedirectUri,
    String frontendSuccessUrl,
    List<String> scopes) {

  public OidcProperties {
    scopes =
        List.copyOf(
            scopes == null || scopes.isEmpty() ? List.of("openid", "profile", "email") : scopes);
    frontendSuccessUrl = frontendSuccessUrl == null ? "/" : frontendSuccessUrl;
  }
}
