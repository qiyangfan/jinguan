package com.longzhong.jinguan.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter)
      throws Exception {
    return http.csrf(csrf -> csrf.disable()) // API 使用无状态 token 认证。
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                // 放行错误与转发分发，保留原始错误响应。
                auth.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD)
                    .permitAll()
                    .requestMatchers("/auth/**", "/actuator/health")
                    .permitAll()
                    // 指标端点匿名可读，依赖部署网络隔离。
                    .requestMatchers("/actuator/prometheus")
                    .permitAll()
                    // OpenAPI 文档供契约检查和前端类型生成使用。
                    .requestMatchers("/v3/api-docs/**")
                    .permitAll()
                    // 页面骨架可匿名读取；受保护操作由 API 认证。
                    .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**")
                    .permitAll()
                    // 页面白名单与前端路由保持一致。
                    .requestMatchers(
                        HttpMethod.GET,
                        "/wallet",
                        "/payments",
                        "/pricing",
                        "/topology",
                        "/keys",
                        "/login")
                    .permitAll()
                    // muniu 拉取端点目前不带认证头，依赖部署网络隔离。
                    .requestMatchers(
                        HttpMethod.GET,
                        "/control/v1/pricing",
                        "/control/v1/key-revocations",
                        "/control/v1/topology")
                    .permitAll()
                    // ingest 使用独立静态 token，由 IngestController 校验。
                    .requestMatchers("/ingest/**")
                    .permitAll()
                    // 渠道回调的签名由对应适配器校验。
                    .requestMatchers(HttpMethod.POST, "/payments/mock/*/pay")
                    .permitAll()
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthFilter, BasicAuthenticationFilter.class)
        .build();
  }
}
