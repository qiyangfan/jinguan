package com.longzhong.jinguan.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 管理和下发供应商、路由与能力目录；字段对齐 muniu 契约，整包替换并按负载生成 ETag。 */
@Service
public class TopologyService {

  public record KeyRef(@JsonProperty("env") String env, @JsonProperty("weight") int weight) {}

  public record ProviderDef(
      @JsonProperty("name") String name,
      @JsonProperty("adapter") String adapter,
      @JsonProperty("base_url") String baseUrl,
      @JsonProperty("keys") List<KeyRef> keys,
      @JsonProperty("weight") long weight,
      @JsonProperty("priority") long priority) {}

  public record RouteCandidate(
      @JsonProperty("provider") String provider,
      @JsonProperty("model") String model,
      @JsonProperty("priority") long priority,
      @JsonProperty("weight") long weight) {}

  /** 模型能力配置；字段缺省语义与 muniu 一致。 */
  public record CapabilityDef(
      @JsonProperty("provider") String provider,
      @JsonProperty("model") String model,
      @JsonProperty("drop_params") List<String> dropParams,
      @JsonProperty("thinking_lock") boolean thinkingLock,
      @JsonProperty("max_output_tokens") long maxOutputTokens) {}

  public record TopologyPayload(
      @JsonProperty("providers") List<ProviderDef> providers,
      @JsonProperty("model_routes") Map<String, List<RouteCandidate>> modelRoutes,
      @JsonProperty("models") List<CapabilityDef> models) {}

  private final TopologyProviderRepository providers;
  private final TopologyRouteRepository routes;
  private final TopologyModelRepository models;
  private final ObjectMapper mapper;

  public TopologyService(
      TopologyProviderRepository providers,
      TopologyRouteRepository routes,
      TopologyModelRepository models,
      ObjectMapper mapper) {
    this.providers = providers;
    this.routes = routes;
    this.models = models;
    this.mapper = mapper;
  }

  /** 查询当前拓扑；稳定排序保证相同内容生成相同 ETag。 */
  @Transactional(readOnly = true)
  public TopologyPayload current() {
    List<ProviderDef> providerDefs =
        providers.findAll().stream()
            .sorted(Comparator.comparing(TopologyProviderEntity::getName))
            .map(
                e ->
                    new ProviderDef(
                        e.getName(),
                        e.getAdapter(),
                        e.getBaseUrl(),
                        List.of(deserialize(e.getKeysJson(), KeyRef[].class)),
                        e.getWeight(),
                        e.getPriority()))
            .toList();
    Map<String, List<RouteCandidate>> routeDefs = new LinkedHashMap<>();
    routes.findAll().stream()
        .sorted(Comparator.comparing(TopologyRouteEntity::getAlias))
        .forEach(
            e ->
                routeDefs.put(
                    e.getAlias(),
                    List.of(deserialize(e.getCandidatesJson(), RouteCandidate[].class))));
    List<CapabilityDef> modelDefs =
        models.findAll().stream()
            .sorted(
                Comparator.comparing(TopologyModelEntity::getProvider)
                    .thenComparing(TopologyModelEntity::getModel))
            .map(
                e ->
                    new CapabilityDef(
                        e.getProvider(),
                        e.getModel(),
                        e.getDropParamsJson() == null
                            ? List.of()
                            : List.of(deserialize(e.getDropParamsJson(), String[].class)),
                        e.isThinkingLock(),
                        e.getMaxOutputTokens()))
            .toList();
    return new TopologyPayload(providerDefs, routeDefs, modelDefs);
  }

  /** 计算当前拓扑的 ETag。 */
  @Transactional(readOnly = true)
  public String currentEtag() {
    return etagOf(current());
  }

  /** 从响应负载计算 ETag。 */
  public String etagOf(TopologyPayload payload) {
    return quote(hashOf(payload));
  }

  /** 单事务校验并整包替换；model_routes 不可为空，引用须指向已定义的供应商，供应商权重须大于 0，非正 key 权重归一为 1。 */
  @Transactional
  public String replaceAll(TopologyPayload next) {
    if (next == null
        || next.providers() == null
        || next.modelRoutes() == null
        || next.models() == null) {
      throw new IllegalArgumentException("拓扑三件套(providers/model_routes/models)缺一不可");
    }
    if (next.modelRoutes().isEmpty()) {
      throw new IllegalArgumentException("model_routes 不能为空:muniu 拒绝应用空路由拓扑");
    }
    Map<String, ProviderDef> byName = new LinkedHashMap<>();
    for (ProviderDef p : next.providers()) {
      requireText(p.name(), "providers[].name");
      requireText(p.adapter(), "providers[].adapter");
      requireText(p.baseUrl(), "providers[].base_url");
      requireIntDomain(p.weight(), 0, "providers[%s].weight".formatted(p.name()), true);
      requireIntDomain(p.priority(), -1, "providers[%s].priority".formatted(p.name()), true);
      if (p.keys() == null || p.keys().isEmpty()) {
        throw new IllegalArgumentException("provider %s 至少需要一个 env 引用 key".formatted(p.name()));
      }
      List<KeyRef> normalizedKeys = new ArrayList<>();
      for (KeyRef k : p.keys()) {
        requireText(k.env(), "keys[].env");
        normalizedKeys.add(new KeyRef(k.env(), k.weight() <= 0 ? 1 : k.weight()));
      }
      if (byName.put(
              p.name(),
              new ProviderDef(
                  p.name(), p.adapter(), p.baseUrl(), normalizedKeys, p.weight(), p.priority()))
          != null) {
        throw new IllegalArgumentException("provider %s 重复定义".formatted(p.name()));
      }
    }
    for (Map.Entry<String, List<RouteCandidate>> entry : next.modelRoutes().entrySet()) {
      requireText(entry.getKey(), "model_routes alias");
      if (entry.getValue() == null || entry.getValue().isEmpty()) {
        throw new IllegalArgumentException("alias %s 候选列表不能为空".formatted(entry.getKey()));
      }
      for (RouteCandidate c : entry.getValue()) {
        requireText(c.provider(), "model_routes[].provider");
        requireText(c.model(), "model_routes[].model");
        requireIntDomain(c.weight(), Long.MIN_VALUE, "model_routes[].weight", false);
        requireIntDomain(c.priority(), -1, "model_routes[].priority", true);
        if (!byName.containsKey(c.provider())) {
          throw new IllegalArgumentException(
              "alias %s 引用未定义的 provider %s(悬空引用)".formatted(entry.getKey(), c.provider()));
        }
      }
    }
    List<CapabilityDef> modelDefs = new ArrayList<>();
    for (CapabilityDef m : next.models()) {
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
      modelDefs.add(m);
    }

    Instant now = Instant.now();
    routes.deleteAll();
    models.deleteAll();
    providers.deleteAll();
    for (Map.Entry<String, List<RouteCandidate>> entry : next.modelRoutes().entrySet()) {
      TopologyRouteEntity row = new TopologyRouteEntity();
      row.setAlias(entry.getKey());
      row.setCandidatesJson(serialize(entry.getValue()));
      row.setUpdatedAt(now);
      routes.save(row);
    }
    for (CapabilityDef m : modelDefs) {
      TopologyModelEntity row = new TopologyModelEntity();
      row.setProvider(m.provider());
      row.setModel(m.model());
      row.setDropParamsJson(
          m.dropParams() == null || m.dropParams().isEmpty() ? null : serialize(m.dropParams()));
      row.setThinkingLock(m.thinkingLock());
      row.setMaxOutputTokens(m.maxOutputTokens());
      row.setUpdatedAt(now);
      models.save(row);
    }
    for (ProviderDef p : byName.values()) {
      TopologyProviderEntity row = new TopologyProviderEntity();
      row.setName(p.name());
      row.setAdapter(p.adapter());
      row.setBaseUrl(p.baseUrl());
      row.setKeysJson(serialize(p.keys()));
      row.setWeight(p.weight());
      row.setPriority(p.priority());
      row.setUpdatedAt(now);
      providers.save(row);
    }
    // PUT 与下发共用同一排序后的拓扑计算 ETag。
    return currentEtag();
  }

  /** 读取拓扑 JSON；数据损坏时拒绝下发。 */
  private <T> T[] deserialize(String json, Class<T[]> type) {
    try {
      return mapper.readValue(json, type);
    } catch (JacksonException e) {
      throw new IllegalStateException("拓扑 JSON 列损坏,读取被拒绝: " + e.getOriginalMessage(), e);
    }
  }

  private String serialize(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JacksonException e) {
      throw new IllegalStateException("拓扑 JSON 序列化失败", e);
    }
  }

  private String hashOf(TopologyPayload payload) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(serialize(payload).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 8);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 不可用", e);
    }
  }

  private String quote(String hex) {
    return "\"" + hex + "\"";
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " 不能为空");
    }
  }

  private static final long INT_DOMAIN_MAX = Integer.MAX_VALUE;

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
