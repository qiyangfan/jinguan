package com.longzhong.jinguan.security;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/** 前端页面路径回退到 index.html；静态资源由 web 构建到 classpath:/static。 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/**")
        .addResourceLocations("classpath:/static/")
        .resourceChain(true)
        .addResolver(
            new PathResourceResolver() {
              @Override
              protected Resource getResource(String resourcePath, Resource location)
                  throws IOException {
                Resource requested = location.createRelative(resourcePath);
                if (requested.exists() && requested.isReadable()) {
                  return requested;
                }
                // 仅回退无扩展名的页面路径。
                if (resourcePath.contains(".") || resourcePath.startsWith("api/")) {
                  return null;
                }
                // 测试环境缺少前端产物时返回 404。
                Resource fallback = location.createRelative("index.html");
                return fallback.exists() && fallback.isReadable() ? fallback : null;
              }
            });
  }
}
