package com.longzhong.jinguan.security;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * SPA 回退:前端路由(/wallet /payments /pricing /keys /login)刷新时无对应 服务端资源,回退 index.html 由前端路由接管;认证由前端路由守卫
 * + API 层 双重承担(标准 SPA 安全模型——后端只保护 API,页面骨架匿名可取)。 静态产物由 web/ 构建输出至 classpath:/static/(见
 * web/vite.config.ts)。
 */
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
                // API 未命中/未知路径不回退:仅无扩展名的页面路径回退 index.html
                if (resourcePath.contains(".") || resourcePath.startsWith("api/")) {
                  return null;
                }
                return location.createRelative("index.html");
              }
            });
  }
}
