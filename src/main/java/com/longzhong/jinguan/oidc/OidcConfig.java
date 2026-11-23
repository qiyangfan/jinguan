package com.longzhong.jinguan.oidc;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 启用Keycloak OIDC RP 配置。 */
@Configuration
@EnableConfigurationProperties(OidcProperties.class)
public class OidcConfig {}
