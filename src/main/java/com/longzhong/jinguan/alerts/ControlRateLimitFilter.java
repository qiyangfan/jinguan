package com.longzhong.jinguan.alerts;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/** 控制面限流:per-service/tenant/IP 固定窗口;超限 429 + Retry-After,并记录审计。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ControlRateLimitFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(ControlRateLimitFilter.class);
  private static final long WINDOW_SECONDS = 60;

  private final com.github.benmanes.caffeine.cache.Cache<String, AtomicLong> counters;
  private final ObjectMapper mapper;
  private final AuditRecorder recorder;
  private final int perMinute;
  private final boolean trustedProxy;

  public ControlRateLimitFilter(
      ObjectMapper mapper,
      AuditRecorder recorder,
      @Value("${jinguan.control.rate-limit.per-minute:600}") int perMinute,
      @Value("${jinguan.control.rate-limit.trusted-proxy:false}") boolean trustedProxy) {
    super();
    this.mapper = mapper;
    this.recorder = recorder;
    this.perMinute = perMinute;
    this.trustedProxy = trustedProxy;
    this.counters =
        com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(WINDOW_SECONDS))
            .maximumSize(200_000)
            .build();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!applies(request) || perMinute <= 0) {
      chain.doFilter(request, response);
      return;
    }
    String bucket = bucketKey(request);
    AtomicLong counter = counters.get(bucket, key -> new AtomicLong());
    if (counter.incrementAndGet() > perMinute) {
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setHeader("Retry-After", Long.toString(WINDOW_SECONDS));
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      mapper.writeValue(
          response.getOutputStream(),
          ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "控制面请求过于频繁,请稍后再试"));
      try {
        recorder.record(
            AuditActor.system(),
            AuditActions.RATE_LIMITED,
            "rate-limit",
            bucket,
            null,
            null,
            Map.of("path", request.getRequestURI(), "limit", perMinute),
            "DENIED");
      } catch (RuntimeException e) {
        log.warn("限流审计写入失败: {}", e.getMessage());
      }
      return;
    }
    chain.doFilter(request, response);
  }

  private static boolean applies(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/control/")
        || path.startsWith("/ingest/")
        || path.startsWith("/admin/");
  }

  private String bucketKey(HttpServletRequest request) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated()) {
      Object principal = auth.getPrincipal();
      if (principal instanceof com.longzhong.jinguan.security.JwtService.LoginUser user) {
        return "user:" + user.subject();
      }
      if (principal != null) {
        return "principal:" + principal;
      }
    }
    String serviceId = request.getHeader("X-Service-Id");
    if (serviceId != null && !serviceId.isBlank()) {
      return "service:" + serviceId;
    }
    return "ip:" + clientIp(request);
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
