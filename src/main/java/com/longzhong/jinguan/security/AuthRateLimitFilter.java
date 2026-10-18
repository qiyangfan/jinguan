package com.longzhong.jinguan.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * 登录/注册防爆破:per-IP 固定窗口限流(login/register 各自独立桶)。 IP 解析仅在显式声明可信反代时才采信 X-Forwarded-For 首值,否则一律
 * remoteAddr—— 防伪造头绕过。超限返回 429 ProblemDetail,窗口滑过自然恢复。 集群化需换分布式限流(README 演进项);单实例语义与
 * PaymentExpiryScheduler 的 ShedLock 登记一致。
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

  private final com.github.benmanes.caffeine.cache.Cache<String, AtomicLong> counters;
  private final ObjectMapper mapper;
  private final int loginPerMinute;
  private final int registerPerMinute;
  private final boolean trustedProxy;

  public AuthRateLimitFilter(
      ObjectMapper mapper,
      @Value("${jinguan.security.rate-limit.login-per-minute}") int loginPerMinute,
      @Value("${jinguan.security.rate-limit.register-per-minute}") int registerPerMinute,
      @Value("${jinguan.security.rate-limit.trusted-proxy}") boolean trustedProxy) {
    this.mapper = mapper;
    this.loginPerMinute = loginPerMinute;
    this.registerPerMinute = registerPerMinute;
    this.trustedProxy = trustedProxy;
    // 窗口 = 首次请求起 60s(计数器原位自增不重置过期);容量上限防内存打爆
    this.counters =
        com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(100_000)
            .build();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    int limit = limitFor(request);
    if (limit < 0) {
      chain.doFilter(request, response);
      return;
    }
    String bucket = clientIp(request) + ":" + request.getRequestURI();
    AtomicLong counter = counters.get(bucket, k -> new AtomicLong());
    if (counter.incrementAndGet() > limit) {
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      mapper.writeValue(
          response.getOutputStream(),
          ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁,请稍后再试"));
      return;
    }
    chain.doFilter(request, response);
  }

  private int limitFor(HttpServletRequest request) {
    if (!"POST".equals(request.getMethod())) {
      return -1;
    }
    return switch (request.getRequestURI()) {
      case "/auth/login" -> loginPerMinute;
      case "/auth/register" -> registerPerMinute;
      default -> -1;
    };
  }

  private String clientIp(HttpServletRequest request) {
    if (trustedProxy) {
      String forwarded = request.getHeader("X-Forwarded-For");
      if (forwarded != null && !forwarded.isBlank()) {
        return forwarded.split(",")[0].trim();
      }
    }
    return request.getRemoteAddr();
  }
}
