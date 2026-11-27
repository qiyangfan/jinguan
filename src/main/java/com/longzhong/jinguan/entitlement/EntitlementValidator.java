package com.longzhong.jinguan.entitlement;

import java.util.regex.Pattern;

/** 套餐字段基础校验。 */
final class EntitlementValidator {

  static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9_-]{1,62}");
  private static final int MAX_NAME_LENGTH = 128;

  private EntitlementValidator() {}

  static String requirePlanKey(String planKey) {
    if (planKey == null || !KEY.matcher(planKey).matches()) {
      throw new IllegalArgumentException("plan_key 需匹配 [a-z0-9][a-z0-9_-]{1,62}");
    }
    return planKey;
  }

  static String requireName(String name) {
    if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
      throw new IllegalArgumentException("套餐名称必填且不超过 128 字符");
    }
    return name.trim();
  }

  static void requireReason(String reason) {
    if (reason == null || reason.isBlank() || reason.length() > 512) {
      throw new IllegalArgumentException("变更原因必填且不超过 512 字符");
    }
  }
}
