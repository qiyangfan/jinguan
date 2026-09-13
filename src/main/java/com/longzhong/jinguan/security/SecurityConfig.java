package com.longzhong.jinguan.security;

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
    return http.csrf(csrf -> csrf.disable()) // 纯 token API,无浏览器会话
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/auth/**", "/actuator/health")
                    .permitAll()
                    // OpenAPI 契约端点匿名可读:三根管子跨仓契约比对与前端类型生成的数据源
                    .requestMatchers("/v3/api-docs/**")
                    .permitAll()
                    // SPA 页面骨架与静态资源匿名可取(认证由前端路由守卫 + API 层承担)
                    .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/wallet", "/payments", "/pricing", "/keys", "/login")
                    .permitAll()
                    // muniu 拉取端点:数据面 GET 无认证头(现实契约),靠部署网络隔离保护;
                    // muniu 侧增加拉取认证头为后续演进项
                    .requestMatchers(
                        HttpMethod.GET, "/control/v1/pricing", "/control/v1/key-revocations")
                    .permitAll()
                    // ingest 的 Bearer 是独立静态 token(非用户 JWT),由 IngestController 自校验
                    .requestMatchers("/ingest/**")
                    .permitAll()
                    // 渠道回调/webhook 形态端点:渠道侧服务对服务调用(mock 收银台同形态),
                    // 真实 PSP 的合法性由各 adapter 验签保证
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
