package com.longzhong.jinguan.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * 拓扑三件套(供应商/模型路由/能力目录)运营态与下发契约。负载形态与 muniu
 * TopologyPayload(internal/config/topology_source.go)逐字段同构;管理侧 `PUT /admin/topology` 整包替换(与 muniu
 * ReplaceTopology 整体换新语义同构, 杜绝「providers 已换、routes 指旧 provider」的中间态被拉取循环捞走)。 ETag = 下发 payload
 * 的内容哈希:PUT 相同内容的重放不改 ETag,muniu 拉取自然 304 短路。
 */
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

  /** muniu 侧缺省语义:drop_params 空=零干预;thinking_lock false;max_output_tokens 0=不限制。 */
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

  /** 当前拓扑(整包):稳定排序保证同内容序列化字节一致(ETag 内容寻址的前提)。 */
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

  /** 当前下发 ETag(双引号包裹的内容哈希;空拓扑同样有确定哈希)。 */
  @Transactional(readOnly = true)
  public String currentEtag() {
    return etagOf(current());
  }

  /** ETag 必须从与响应体同一个 payload 对象派生(单次组装),杜绝 body 与 ETag 错配。 */
  public String etagOf(TopologyPayload payload) {
    return quote(hashOf(payload));
  }

  /**
   * 整包替换(单事务 delete-all + insert-all)。校验:model_routes 非空(muniu 拒绝空 routes, 「下线全部路由」不是合法稳态,两侧同规)/
   * routes 与 models 的 provider 引用必须存在于本次 提交的 providers(悬空引用即「半拓扑」)/ provider weight > 0(与 muniu
   * 加载校验同规); key weight ≤ 0 归一为 1(存储即下发,与 muniu YAML 缺省语义一致)。
   */
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
    // ETag 与下发同源:从库重新组装 current() 计算,保证 PUT 返回值 ≡ 下发值(排序归一)
    return currentEtag();
  }

  /** 序列化失败的 JSON 列在读取时拒绝(损坏数据不外泄,修复前宁可下发失败保持 muniu 降级)。 */
  private <T> T[] deserialize(String json, Class<T[]> type) {
    try {
      return mapper.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("拓扑 JSON 列损坏,读取被拒绝: " + e.getOriginalMessage(), e);
    }
  }

  private String serialize(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
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

  /**
   * 数值域守门:weight/priority 的下发契约是数据面 int 语义(muniu TopologyPayload 接收形态), 录入侧拦住超出 int
   * 域的值——荒谬量级一旦下发,故障落在转发面权重运算且跨仓无从定位。 minExclusive 传 0 表示「必须 >0」(provider weight 语义);其余只做上界约束。
   */
  private static final long INT_DOMAIN_MAX = Integer.MAX_VALUE;

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
