package com.longzhong.jinguan.entitlement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 配额维度校验:维度/单位/限值显式,维度唯一。 */
@Component
class QuotaValidation {

  static final Pattern DIMENSION = Pattern.compile("[a-z][a-z0-9_.-]{1,63}");
  private static final int MAX_QUOTAS = 100;
  private static final int MAX_UNIT_LENGTH = 32;

  List<EntitlementViews.QuotaView> normalizeQuotas(List<EntitlementViews.QuotaView> input) {
    if (input == null || input.isEmpty()) {
      return List.of();
    }
    if (input.size() > MAX_QUOTAS) {
      throw new IllegalArgumentException("配额维度过多(上限 100)");
    }
    Set<String> dimensions = new HashSet<>();
    List<EntitlementViews.QuotaView> normalized = new ArrayList<>();
    for (EntitlementViews.QuotaView quota : input) {
      normalized.add(validateQuota(quota, dimensions));
    }
    return List.copyOf(normalized);
  }

  private EntitlementViews.QuotaView validateQuota(
      EntitlementViews.QuotaView quota, Set<String> dimensions) {
    String dimension = requireDimension(quota);
    String unit = requireUnit(quota);
    requireLimit(quota);
    if (!dimensions.add(dimension)) {
      throw new IllegalArgumentException("配额 dimension 重复: " + dimension);
    }
    return new EntitlementViews.QuotaView(dimension, unit, quota.limit());
  }

  private static String requireDimension(EntitlementViews.QuotaView quota) {
    if (quota == null
        || quota.dimension() == null
        || !DIMENSION.matcher(quota.dimension()).matches()) {
      throw new IllegalArgumentException("配额 dimension 需匹配 [a-z][a-z0-9_.-]{1,63}");
    }
    return quota.dimension();
  }

  private static String requireUnit(EntitlementViews.QuotaView quota) {
    if (quota.unit() == null || quota.unit().isBlank() || quota.unit().length() > MAX_UNIT_LENGTH) {
      throw new IllegalArgumentException("配额 unit 必填且不超过 32 字符");
    }
    return quota.unit();
  }

  private static void requireLimit(EntitlementViews.QuotaView quota) {
    if (quota.limit() < 0) {
      throw new IllegalArgumentException("配额 limit 必须非负");
    }
  }
}
