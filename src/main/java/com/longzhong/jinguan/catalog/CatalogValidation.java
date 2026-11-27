package com.longzhong.jinguan.catalog;

import com.longzhong.jinguan.registry.ServiceRegistryService;
import com.longzhong.jinguan.tenant.TenantQueryService;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 目录条目输入校验:可见性/租户/权益键/运营字段;不复制 Bot 定义。 */
@Component
class CatalogValidation {

  static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9_.-]{1,63}");
  private static final Set<String> VISIBILITIES = Set.of("PUBLIC", "TENANT");
  private static final String TENANT_VISIBILITY = "TENANT";
  private static final int MAX_ENTITLEMENT_KEY = 128;

  private final TenantQueryService tenants;
  private final ServiceRegistryService registry;

  CatalogValidation(TenantQueryService tenants, ServiceRegistryService registry) {
    this.tenants = tenants;
    this.registry = registry;
  }

  void requireReason(String reason) {
    if (reason == null || reason.isBlank() || reason.length() > 512) {
      throw new IllegalArgumentException("变更原因必填且不超过 512 字符");
    }
  }

  String requireEntryKey(String entryKey) {
    if (entryKey == null || !KEY.matcher(entryKey).matches()) {
      throw new IllegalArgumentException("entry_key 需匹配 [a-z0-9][a-z0-9_.-]{1,63}");
    }
    return entryKey;
  }

  String requireBotKey(String botKey) {
    if (botKey == null || botKey.isBlank() || botKey.length() > 128) {
      throw new IllegalArgumentException("botKey 必填且不超过 128 字符");
    }
    return botKey;
  }

  Normalized normalize(CatalogService.EntryInput input) {
    String title = requireTitle(input.title());
    String summary = requireSummary(input.summary());
    String visibility = normalizeVisibility(input.visibility());
    String tenantId = null;
    String tenantKey = null;
    if (TENANT_VISIBILITY.equals(visibility)) {
      if (input.tenantId() == null || input.tenantId().isBlank()) {
        throw new IllegalArgumentException("TENANT 可见性必须提供 tenantId");
      }
      tenantKey = tenants.requireActive(input.tenantId()).tenantKey();
      tenantId = input.tenantId();
    }
    String entitlementKey = normalizeEntitlementKey(input.entitlementKey());
    return new Normalized(title, summary, visibility, tenantId, tenantKey, entitlementKey);
  }

  private static String requireTitle(String title) {
    if (title == null || title.isBlank() || title.length() > 128) {
      throw new IllegalArgumentException("标题必填且不超过 128 字符");
    }
    return title.trim();
  }

  private static String requireSummary(String summary) {
    if (summary != null && summary.length() > 512) {
      throw new IllegalArgumentException("摘要不超过 512 字符");
    }
    return summary;
  }

  private static String normalizeVisibility(String visibility) {
    String normalized = visibility == null ? "" : visibility.trim().toUpperCase(Locale.ROOT);
    if (!VISIBILITIES.contains(normalized)) {
      throw new IllegalArgumentException("visibility 只能是 PUBLIC/TENANT");
    }
    return normalized;
  }

  private String normalizeEntitlementKey(String entitlementKey) {
    if (entitlementKey == null || entitlementKey.isBlank()) {
      return null;
    }
    if (entitlementKey.length() > MAX_ENTITLEMENT_KEY) {
      throw new IllegalArgumentException("entitlementKey 不超过 128 字符");
    }
    if (!registry.capabilityCatalog().contains(entitlementKey)) {
      throw new IllegalArgumentException("entitlementKey 未在服务注册能力目录中声明: " + entitlementKey);
    }
    return entitlementKey;
  }

  /** 规范化后的条目字段;tenantId/tenantKey/entitlementKey 可为 null。 */
  record Normalized(
      String title,
      String summary,
      String visibility,
      String tenantId,
      String tenantKey,
      String entitlementKey) {}
}
