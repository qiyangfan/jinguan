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
    return http.csrf(csrf -> csrf.disable()) // 纯 token API,无浏览器会话
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                // Security 7 起授权覆盖全部 dispatch(ERROR/FORWARD 一并过滤):容器 error
                // 页(/error)若不放行,任何 sendError 都会被顶成 403 空体,掩盖真实状态码
                auth.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD)
                    .permitAll()
                    .requestMatchers("/auth/**", "/actuator/health")
                    .permitAll()
                    // 指标抓取端点:与 /control/v1/* 同信任前提(匿名可读 + 部署网络隔离,
                    // compose 发布形态为 127.0.0.1:8090);Prometheus 侧持不下用户 JWT(access 30min 轮换)。
                    // 「数据面拉取 + 采集面统一加认证头」是 README 演进登记的同一项,多主机部署前必须先落地
                    .requestMatchers("/actuator/prometheus")
                    .permitAll()
                    // OpenAPI 契约端点匿名可读:三根管子跨仓契约比对与前端类型生成的数据源
                    .requestMatchers("/v3/api-docs/**")
                    .permitAll()
                    // SPA 页面骨架与静态资源匿名可取(认证由前端路由守卫 + API 层承担);
                    // 白名单须与前端路由清单同步——漏项会让该页深链/刷新 403(拓扑页曾漏)
                    .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/wallet",
                        "/payments",
                        "/pricing",
                        "/topology",
                        "/keys",
                        "/login")
                    .permitAll()
                    // muniu 拉取端点:数据面 GET 无认证头(现实契约),靠部署网络隔离保护;
                    // muniu 侧增加拉取认证头为后续演进项
                    .requestMatchers(
                        HttpMethod.GET,
                        "/control/v1/pricing",
                        "/control/v1/key-revocations",
                        "/control/v1/topology")
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
