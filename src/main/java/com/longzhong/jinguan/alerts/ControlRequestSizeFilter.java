package com.longzhong.jinguan.alerts;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/** 控制面请求体大小上限:超限直接 413,避免大载荷占用资源。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ControlRequestSizeFilter extends OncePerRequestFilter {

  private final ObjectMapper mapper;
  private final long maxBytes;

  public ControlRequestSizeFilter(
      ObjectMapper mapper, @Value("${jinguan.control.max-request-bytes:1048576}") long maxBytes) {
    super();
    this.mapper = mapper;
    this.maxBytes = maxBytes;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (applies(request) && request.getContentLengthLong() > maxBytes) {
      response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      mapper.writeValue(
          response.getOutputStream(),
          ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "控制面请求体超过上限"));
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
}
