package com.longzhong.jinguan.policy;

import com.longzhong.jinguan.serviceidentity.ServiceAuthService;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 策略包输入校验:包键/作用域/规则集引用/service_id 目录一致性。 */
@Component
class PolicyValidation {

  static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9_-]{1,62}");
  private static final Set<String> SCOPES = Set.of("GLOBAL", "TENANT");
  private static final int MAX_RULESET_VERSION = 64;

  private final ServiceAuthService serviceAuth;

  PolicyValidation(ServiceAuthService serviceAuth) {
    this.serviceAuth = serviceAuth;
  }

  void requireReason(String reason) {
    if (reason == null || reason.isBlank() || reason.length() > 512) {
      throw new IllegalArgumentException("变更原因必填且不超过 512 字符");
    }
  }

  String requirePackageKey(String packageKey) {
    if (packageKey == null || !KEY.matcher(packageKey).matches()) {
      throw new IllegalArgumentException("package_key 需匹配 [a-z0-9][a-z0-9_-]{1,62}");
    }
    return packageKey;
  }

  String requireKnownService(String serviceId) {
    if (serviceId == null || serviceId.isBlank()) {
      throw new IllegalArgumentException("serviceId 必填");
    }
    boolean known =
        serviceAuth.directory().stream().anyMatch(entry -> serviceId.equals(entry.serviceId()));
    if (!known) {
      throw new IllegalArgumentException("serviceId 不在服务身份目录: " + serviceId);
    }
    return serviceId;
  }

  String normalizeScope(String scope) {
    String normalized = scope == null ? "" : scope.trim().toUpperCase(Locale.ROOT);
    if (!SCOPES.contains(normalized)) {
      throw new IllegalArgumentException("scope 只能是 GLOBAL/TENANT");
    }
    return normalized;
  }

  String normalizeRulesetVersion(String rulesetVersion) {
    if (rulesetVersion == null || rulesetVersion.isBlank()) {
      return null;
    }
    if (rulesetVersion.length() > MAX_RULESET_VERSION) {
      throw new IllegalArgumentException("rulesetVersion 不超过 64 字符");
    }
    return rulesetVersion.trim();
  }
}
