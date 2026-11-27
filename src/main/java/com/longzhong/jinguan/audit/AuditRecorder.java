package com.longzhong.jinguan.audit;

import com.longzhong.jinguan.common.Redaction;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 写控制面审计:统一入口,summary 递归脱敏,request_id 取自 MDC。 */
@Service
public class AuditRecorder {

  public static final String AUDIT_ID_ATTRIBUTE = "jinguan.auditId";

  private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

  private final ControlAuditRepository repository;
  private final ObjectMapper mapper;

  public AuditRecorder(ControlAuditRepository repository, ObjectMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

  /** 记录一条审计;结果为 SUCCESS/DENIED/FAILED。 */
  @SuppressWarnings("PMD.UseObjectForClearerAPI") // 审计字段均为独立必填维度,容器对象只增样板
  @Transactional
  public String record(
      AuditActor actor,
      String action,
      String targetType,
      String targetId,
      String tenantKey,
      String reason,
      Map<String, Object> summary,
      String result) {
    String summaryJson =
        summary == null || summary.isEmpty() ? null : serialize(Redaction.redact(summary));
    String id = UUID.randomUUID().toString();
    repository.save(
        ControlAuditEntity.of(
            new AuditRecord(
                id,
                Instant.now(),
                actor,
                action,
                targetType,
                targetId,
                tenantKey,
                reason == null ? null : truncate(reason, 512),
                summaryJson,
                result,
                MDC.get("requestId"))));
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      attributes.setAttribute(AUDIT_ID_ATTRIBUTE, id, RequestAttributes.SCOPE_REQUEST);
    }
    return id;
  }

  private String serialize(Map<String, Object> summary) {
    try {
      return mapper.writeValueAsString(summary);
    } catch (JacksonException e) {
      log.warn("审计摘要序列化失败,以键集代替: {}", e.getOriginalMessage());
      return summary.keySet().stream()
          .map(k -> "\"" + k + "\":\"" + Redaction.MASK + "\"")
          .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
  }

  private static String truncate(String value, int max) {
    String trimmed = value.trim();
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }
}
