package com.longzhong.jinguan.serviceidentity;

import com.longzhong.jinguan.alerts.AlertService;
import com.longzhong.jinguan.alerts.AlertTypes;
import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import com.longzhong.jinguan.common.DomainException;
import com.longzhong.jinguan.common.ForbiddenException;
import com.longzhong.jinguan.common.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 服务身份拒绝:统一 401/403 语义、审计与去重告警。 */
@Component
class ServiceAuthRejector {

  private static final Logger log = LoggerFactory.getLogger(ServiceAuthRejector.class);

  private final ServiceAuthProperties properties;
  private final AuditRecorder recorder;
  private final AlertService alerts;

  ServiceAuthRejector(
      ServiceAuthProperties properties, AuditRecorder recorder, AlertService alerts) {
    this.properties = properties;
    this.recorder = recorder;
    this.alerts = alerts;
  }

  DomainException reject(
      String serviceId,
      String scope,
      HttpServletRequest request,
      String reason,
      boolean unauthorized) {
    final String subject = serviceId == null || serviceId.isBlank() ? "unknown" : serviceId;
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("scope", scope == null ? "" : scope);
    summary.put("mode", properties.mode());
    summary.put("remote", request.getRemoteAddr());
    summary.put("reason", reason);
    recorder.record(
        AuditActor.service(subject),
        AuditActions.SERVICE_AUTH_REJECT,
        "service-identity",
        subject,
        null,
        reason,
        summary,
        "DENIED");
    alerts.raise(
        AlertTypes.AUTH_FAILURE,
        "WARNING",
        "service-auth",
        null,
        serviceId,
        "auth:" + subject + ":" + reason,
        Map.of("reason", reason, "scope", scope == null ? "" : scope),
        "docs/service-identity-runbook.md");
    log.warn("服务身份拒绝 service={} reason={}", subject, reason);
    return unauthorized ? new UnauthorizedException(reason) : new ForbiddenException(reason);
  }

  void requireScope(
      String serviceId, List<String> scopes, String requiredScope, HttpServletRequest request) {
    if (requiredScope != null && !scopes.contains(requiredScope)) {
      throw reject(serviceId, requiredScope, request, "服务身份缺少 scope: " + requiredScope, false);
    }
  }
}
