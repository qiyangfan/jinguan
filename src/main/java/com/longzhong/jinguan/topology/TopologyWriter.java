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
    TopologyRouteEntity row = new TopologyRouteEntity();
    row.setAlias(alias);
    row.setCandidatesJson(codec.serialize(candidates));
    row.setUpdatedAt(now);
    return row;
  }

  private TopologyModelEntity modelRow(TopologyService.CapabilityDef model, Instant now) {
    TopologyModelEntity row = new TopologyModelEntity();
    row.setProvider(model.provider());
    row.setModel(model.model());
    row.setDropParamsJson(
        model.dropParams() == null || model.dropParams().isEmpty()
            ? null
            : codec.serialize(model.dropParams()));
    row.setThinkingLock(model.thinkingLock());
    row.setMaxOutputTokens(model.maxOutputTokens());
    row.setUpdatedAt(now);
    return row;
  }

  private TopologyProviderEntity providerRow(TopologyService.ProviderDef provider, Instant now) {
    TopologyProviderEntity row = new TopologyProviderEntity();
    row.setName(provider.name());
    row.setAdapter(provider.adapter());
    row.setBaseUrl(provider.baseUrl());
    row.setKeysJson(codec.serialize(provider.keys()));
    row.setWeight(provider.weight());
    row.setPriority(provider.priority());
    row.setUpdatedAt(now);
    return row;
  }
}
