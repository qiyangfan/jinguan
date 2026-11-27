package com.longzhong.jinguan.saga;

import java.util.Map;
import java.util.regex.Pattern;

/** 流程启动参数校验:仅支持枚举流程类型与白名单参数。 */
final class SagaValidation {

  private static final Pattern TENANT_KEY = Pattern.compile("[a-z0-9][a-z0-9_-]{1,62}");
  private static final Pattern PLAN_KEY = Pattern.compile("[a-z0-9][a-z0-9_-]{1,62}");
  private static final int MAX_NAME = 128;
  private static final int MAX_IDEMPOTENCY_KEY = 128;
  private static final int MAX_REASON = 512;

  private SagaValidation() {}

  /** 校验后的启动参数。 */
  record StartParams(
      String type, String tenantKey, String name, String planKey, String idempotencyKey) {}

  static StartParams validate(
      String type,
      String idempotencyKey,
      Map<String, Object> params,
      String reason,
      boolean confirmed) {
    requireStartable(type, confirmed, reason);
    String tenantKey = requireTenantKey(stringParam(params, SagaStatus.CONTEXT_TENANT_KEY));
    String name = requireName(stringParam(params, SagaStatus.CONTEXT_NAME));
    String planKey = requirePlanKey(stringParam(params, SagaStatus.CONTEXT_PLAN_KEY));
    return new StartParams(type, tenantKey, name, planKey, normalizeIdempotencyKey(idempotencyKey));
  }

  private static void requireStartable(String type, boolean confirmed, String reason) {
    if (!SagaStatus.TENANT_ONBOARD.equals(type)) {
      throw new IllegalArgumentException("不支持的流程类型: " + type);
    }
    if (!confirmed) {
      throw new IllegalArgumentException("启动跨域流程必须显式确认");
    }
    if (reason == null || reason.isBlank() || reason.length() > MAX_REASON) {
      throw new IllegalArgumentException("变更原因必填且不超过 512 字符");
    }
  }

  private static String requireTenantKey(String tenantKey) {
    if (tenantKey == null || !TENANT_KEY.matcher(tenantKey).matches()) {
      throw new IllegalArgumentException("tenantKey 需匹配 [a-z0-9][a-z0-9_-]{1,62}");
    }
    return tenantKey;
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank() || name.length() > MAX_NAME) {
      throw new IllegalArgumentException("name 必填且不超过 128 字符");
    }
    return name;
  }

  private static String requirePlanKey(String planKey) {
    if (planKey == null || !PLAN_KEY.matcher(planKey).matches()) {
      throw new IllegalArgumentException("planKey 需匹配 [a-z0-9][a-z0-9_-]{1,62}");
    }
    return planKey;
  }

  private static String normalizeIdempotencyKey(String idempotencyKey) {
    String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
    if (key != null && key.length() > MAX_IDEMPOTENCY_KEY) {
      throw new IllegalArgumentException("idempotencyKey 不超过 128 字符");
    }
    return key;
  }

  private static String stringParam(Map<String, Object> params, String key) {
    if (params == null) {
      return null;
    }
    Object value = params.get(key);
    return value instanceof String text && !text.isBlank() ? text.trim() : null;
  }
}
