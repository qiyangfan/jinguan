package com.longzhong.jinguan.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 把本次请求最近一条审计 id 回写为 X-Audit-Id,供控制台展示审计回执。 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AuditReceiptFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Audit-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      chain.doFilter(request, response);
    } finally {
      Object auditId = request.getAttribute(AuditRecorder.AUDIT_ID_ATTRIBUTE);
      if (auditId != null && !response.isCommitted()) {
        response.setHeader(HEADER, auditId.toString());
      }
    }
  }
}
