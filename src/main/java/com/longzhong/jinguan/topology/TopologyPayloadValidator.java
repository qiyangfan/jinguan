package com.longzhong.jinguan.topology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 校验整包拓扑并归一 key 权重；规则与 muniu 应用侧一致。 */
@Component
class TopologyPayloadValidator {

  record Validated(
      Map<String, TopologyService.ProviderDef> providers,
      Map<String, List<TopologyService.RouteCandidate>> routes,
      List<TopologyService.CapabilityDef> models) {}

  private static final long INT_DOMAIN_MAX = Integer.MAX_VALUE;

  Validated validate(TopologyService.TopologyPayload next) {
    requireShape(next);
    Map<String, TopologyService.ProviderDef> byName = new LinkedHashMap<>();
    for (TopologyService.ProviderDef p : next.providers()) {
      TopologyService.ProviderDef normalized = normalizeProvider(p);
      if (byName.putIfAbsent(normalized.name(), normalized) != null) {
        throw new IllegalArgumentException("provider %s 重复定义".formatted(p.name()));
      }
    }
    next.modelRoutes().forEach((alias, candidates) -> validateRoute(alias, candidates, byName));
    List<TopologyService.CapabilityDef> models = new ArrayList<>();
    for (TopologyService.CapabilityDef m : next.models()) {
      models.add(validateModel(m, byName));
    }
    return new Validated(byName, next.modelRoutes(), models);
  }

  private void requireShape(TopologyService.TopologyPayload next) {
    if (next == null
        || next.providers() == null
        || next.modelRoutes() == null
        || next.models() == null) {
      throw new IllegalArgumentException("拓扑三件套(providers/model_routes/models)缺一不可");
    }
    if (next.modelRoutes().isEmpty()) {
      throw new IllegalArgumentException("model_routes 不能为空:muniu 拒绝应用空路由拓扑");
    }
  }

  private TopologyService.ProviderDef normalizeProvider(TopologyService.ProviderDef p) {
    requireText(p.name(), "providers[].name");
    requireText(p.adapter(), "providers[].adapter");
    requireText(p.baseUrl(), "providers[].base_url");
    requireIntDomain(p.weight(), 0, "providers[%s].weight".formatted(p.name()), true);
    requireIntDomain(p.priority(), -1, "providers[%s].priority".formatted(p.name()), true);
    if (p.keys() == null || p.keys().isEmpty()) {
      throw new IllegalArgumentException("provider %s 至少需要一个 env 引用 key".formatted(p.name()));
    }
    List<TopologyService.KeyRef> normalizedKeys = new ArrayList<>();
    for (TopologyService.KeyRef k : p.keys()) {
      requireText(k.env(), "keys[].env");
      normalizedKeys.add(new TopologyService.KeyRef(k.env(), k.weight() <= 0 ? 1 : k.weight()));
    }
    return new TopologyService.ProviderDef(
        p.name(), p.adapter(), p.baseUrl(), normalizedKeys, p.weight(), p.priority());
  }

  private void validateRoute(
      String alias,
      List<TopologyService.RouteCandidate> candidates,
      Map<String, TopologyService.ProviderDef> byName) {
    requireText(alias, "model_routes alias");
    if (candidates == null || candidates.isEmpty()) {
      throw new IllegalArgumentException("alias %s 候选列表不能为空".formatted(alias));
    }
    for (TopologyService.RouteCandidate c : candidates) {
      requireText(c.provider(), "model_routes[].provider");
      requireText(c.model(), "model_routes[].model");
      requireIntDomain(c.weight(), Long.MIN_VALUE, "model_routes[].weight", false);
      requireIntDomain(c.priority(), -1, "model_routes[].priority", true);
      if (!byName.containsKey(c.provider())) {
        throw new IllegalArgumentException(
            "alias %s 引用未定义的 provider %s(悬空引用)".formatted(alias, c.provider()));
      }
    }
  }

  private TopologyService.CapabilityDef validateModel(
      TopologyService.CapabilityDef m, Map<String, TopologyService.ProviderDef> byName) {
    requireText(m.provider(), "models[].provider");
    requireText(m.model(), "models[].model");
    if (!byName.containsKey(m.provider())) {
      throw new IllegalArgumentException(
          "能力目录 %s 引用未定义的 provider %s(悬空引用)".formatted(m.model(), m.provider()));
    }
    if (m.dropParams() != null) {
      for (String d : m.dropParams()) {
        requireText(d, "models[].drop_params");
      }
    }
    return m;
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " 不能为空");
    }
  }

  /** 限制在数据面接受的 ±Integer.MAX_VALUE；positiveRequired 为 true 时还须大于 minExclusive。 */
  private void requireIntDomain(
      long value, long minExclusive, String field, boolean positiveRequired) {
    if (positiveRequired && value <= minExclusive) {
      throw new IllegalArgumentException("%s 必须 >%d,实际 %d".formatted(field, minExclusive, value));
    }
    if (value > INT_DOMAIN_MAX || value < -INT_DOMAIN_MAX) {
      throw new IllegalArgumentException(
          "%s 超出数据面数值域(±%d),实际 %d".formatted(field, INT_DOMAIN_MAX, value));
    }
  }
}
