package com.longzhong.jinguan.audit;

import java.time.Instant;

/** 审计写入值对象;summary 已脱敏,id 由调用方生成。 */
public record AuditRecord(
    String id,
    Instant occurredAt,
    AuditActor actor,
    String action,
    String targetType,
    String targetId,
    String tenantKey,
    String reason,
    String summaryJson,
    String result,
    String requestId) {}
