package com.longzhong.jinguan.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理和下发供应商、路由与能力目录；字段对齐 muniu 契约，整包替换并按负载生成 ETag。 */
@Service
public class TopologyService {

  /** 供应商 key 的环境变量引用与权重；明文不入库。 */
  public record KeyRef(@JsonProperty("env") String env, @JsonProperty("weight") int weight) {}

  /** 供应商定义；字段名与 muniu 下发契约一致。 */
  public record ProviderDef(
      @JsonProperty("name") String name,
      @JsonProperty("adapter") String adapter,
      @JsonProperty("base_url") String baseUrl,
      @JsonProperty("keys") List<KeyRef> keys,
      @JsonProperty("weight") long weight,
      @JsonProperty("priority") long priority) {}

  /** 模型路由候选：指向供应商与上游模型，带权重与优先级。 */
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

  /** 拓扑整包：供应商、模型路由与能力目录三件套。 */
  public record TopologyPayload(
      @JsonProperty("providers") List<ProviderDef> providers,
      @JsonProperty("model_routes") Map<String, List<RouteCandidate>> modelRoutes,
      @JsonProperty("models") List<CapabilityDef> models) {}

  private final TopologyProviderRepository providers;
  private final TopologyRouteRepository routes;
  private final TopologyModelRepository models;
  private final TopologyCodec codec;
  private final TopologyPayloadValidator validator;
  private final TopologyWriter writer;

  public TopologyService(
      TopologyProviderRepository providers,
      TopologyRouteRepository routes,
      TopologyModelRepository models,
      TopologyCodec codec,
      TopologyPayloadValidator validator,
      TopologyWriter writer) {
    this.providers = providers;
    this.routes = routes;
    this.models = models;
    this.codec = codec;
    this.validator = validator;
    this.writer = writer;
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
                        List.of(codec.deserialize(e.getKeysJson(), KeyRef[].class)),
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
                    List.of(codec.deserialize(e.getCandidatesJson(), RouteCandidate[].class))));
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
                            : List.of(codec.deserialize(e.getDropParamsJson(), String[].class)),
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
    return codec.etagOf(payload);
  }

  /** 单事务校验并整包替换；model_routes 不可为空，引用须指向已定义的供应商。 */
  @Transactional
  public String replaceAll(TopologyPayload next) {
    writer.replace(validator.validate(next));
    // PUT 与下发共用同一排序后的拓扑计算 ETag。
    return currentEtag();
  }
}
