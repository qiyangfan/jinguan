package com.longzhong.jinguan.tenant;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 租户域输入校验;失败抛 IllegalArgumentException(400)。 */
final class TenantValidation {

  static final Pattern TENANT_KEY = Pattern.compile("[a-z0-9][a-z0-9_-]{1,62}");
  static final Pattern SUBJECT = Pattern.compile("[A-Za-z0-9._:@-]{1,64}");
  private static final Set<String> ROLES = Set.of("OWNER", "ADMIN", "MEMBER");
  private static final int MAX_REASON = 512;
  private static final int MAX_NAME = 128;

  private TenantValidation() {}

  static void requireReason(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("变更原因必填");
    }
    if (reason.length() > MAX_REASON) {
      throw new IllegalArgumentException("变更原因过长(上限 512 字符)");
    }
  }

  static String requireTenantKey(String tenantKey) {
    if (tenantKey == null || !TENANT_KEY.matcher(tenantKey).matches()) {
      throw new IllegalArgumentException("tenant_key 需匹配 [a-z0-9][a-z0-9_-]{1,62}");
    }
    return tenantKey;
  }

  static void validateSubject(String subject) {
    if (subject == null || !SUBJECT.matcher(subject).matches()) {
      throw new IllegalArgumentException("subject 非法");
    }
  }

  static String requireName(String name) {
    if (name == null || name.isBlank() || name.length() > MAX_NAME) {
      throw new IllegalArgumentException("租户名称必填且不超过 128 字符");
    }
    return name.trim();
  }

  static String requireRole(String role) {
    String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    if (!ROLES.contains(normalized)) {
      throw new IllegalArgumentException("粗角色只能是 OWNER/ADMIN/MEMBER");
    }
    return normalized;
  }
}
