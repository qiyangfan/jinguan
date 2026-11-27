package com.longzhong.jinguan.projection;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 启用服务只读投影配置。 */
@Configuration
@EnableConfigurationProperties(ServiceReadProperties.class)
public class ServiceReadConfig {}
