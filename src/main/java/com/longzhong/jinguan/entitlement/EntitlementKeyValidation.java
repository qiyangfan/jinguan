package com.longzhong.jinguan.entitlement;

import com.longzhong.jinguan.registry.ServiceRegistryService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 权益键校验:键必须来自服务注册能力目录。 */
@Component
class EntitlementKeyValidation {

  private static final int MAX_ENTITLEMENTS = 500;
  private static final int MAX_KEY_LENGTH = 128;

  private final ServiceRegistryService registry;

  EntitlementKeyValidation(ServiceRegistryService registry) {
    this.registry = registry;
  }

  List<String> normalizeEntitlements(List<String> input) {
    if (input == null || input.isEmpty()) {
      return List.of();
    }
    if (input.size() > MAX_ENTITLEMENTS) {
      throw new IllegalArgumentException("权益键过多(上限 500)");
    }
    Set<String> catalog = registry.capabilityCatalog();
    Set<String> normalized = new LinkedHashSet<>();
    for (String key : input) {
      normalized.add(validateEntitlement(key, catalog));
    }
    return List.copyOf(normalized);
  }

  private static String validateEntitlement(String key, Set<String> catalog) {
    if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
      throw new IllegalArgumentException("权益键必须非空且不超过 128 字符");
    }
    if (!catalog.contains(key)) {
      throw new IllegalArgumentException("权益键未在服务注册能力目录中声明: " + key);
    }
    return key;
  }
}
