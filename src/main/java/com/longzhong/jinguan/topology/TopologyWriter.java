package com.longzhong.jinguan.topology;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** 把校验后的拓扑整包落库：先清空三表，再按稳定排序写入。 */
@Component
class TopologyWriter {

  private final TopologyProviderRepository providers;
  private final TopologyRouteRepository routes;
  private final TopologyModelRepository models;
  private final TopologyCodec codec;

  TopologyWriter(
      TopologyProviderRepository providers,
      TopologyRouteRepository routes,
      TopologyModelRepository models,
      TopologyCodec codec) {
    this.providers = providers;
    this.routes = routes;
    this.models = models;
    this.codec = codec;
  }

  void replace(TopologyPayloadValidator.Validated topology) {
    final Instant now = Instant.now();
    routes.deleteAll();
    models.deleteAll();
    providers.deleteAll();
    topology.routes().forEach((alias, candidates) -> routes.save(routeRow(alias, candidates, now)));
    topology.models().forEach(model -> models.save(modelRow(model, now)));
    topology.providers().values().forEach(provider -> providers.save(providerRow(provider, now)));
  }

  private TopologyRouteEntity routeRow(
      String alias, List<TopologyService.RouteCandidate> candidates, Instant now) {
    return TopologyRouteEntity.of(alias, codec.serialize(candidates), now);
  }

  private TopologyModelEntity modelRow(TopologyService.CapabilityDef model, Instant now) {
    String dropParamsJson =
        model.dropParams() == null || model.dropParams().isEmpty()
            ? null
            : codec.serialize(model.dropParams());
    return TopologyModelEntity.of(
        model.provider(),
        model.model(),
        dropParamsJson,
        model.thinkingLock(),
        model.maxOutputTokens(),
        now);
  }

  private TopologyProviderEntity providerRow(TopologyService.ProviderDef provider, Instant now) {
    return TopologyProviderEntity.of(
        provider.name(),
        provider.adapter(),
        provider.baseUrl(),
        codec.serialize(provider.keys()),
        provider.weight(),
        provider.priority(),
        now);
  }
}
