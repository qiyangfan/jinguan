package com.longzhong.jinguan.registry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 注册表只读视图组装:健康由 last_seen 推导,不伪造。 */
@Component
class RegistryViewAssembler {

  private final ObjectMapper mapper;
  private final int staleAfterSeconds;

  RegistryViewAssembler(
      ObjectMapper mapper,
      @Value("${jinguan.registry.stale-after-seconds:90}") int staleAfterSeconds) {
    this.mapper = mapper;
    this.staleAfterSeconds = staleAfterSeconds;
  }

  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // 每个新 service_id 仅初始化一次列表
  List<RegistryViews.ServiceView> services(List<ServiceInstanceEntity> instances) {
    Map<String, List<ServiceInstanceEntity>> byService = new TreeMap<>();
    for (ServiceInstanceEntity instance : instances) {
      List<ServiceInstanceEntity> list = byService.get(instance.getServiceId());
      if (list == null) {
        list = new ArrayList<>();
        byService.put(instance.getServiceId(), list);
      }
      list.add(instance);
    }
    List<RegistryViews.ServiceView> views = new ArrayList<>();
    byService.forEach((serviceId, list) -> views.add(viewOf(serviceId, list)));
    return views;
  }

  RegistryViews.ServiceView viewOf(String serviceId, List<ServiceInstanceEntity> list) {
    List<RegistryViews.InstanceView> instanceViews =
        list.stream()
            .sorted(Comparator.comparing(ServiceInstanceEntity::getInstanceId))
            .map(
                instance ->
                    new RegistryViews.InstanceView(
                        instance.getInstanceId(),
                        instance.getEnvironment(),
                        instance.getVersion(),
                        instance.getLastSeenAt(),
                        healthOf(instance.getLastSeenAt())))
            .toList();
    Set<String> capabilities = new TreeSet<>();
    Set<String> adminCapabilities = new TreeSet<>();
    Instant registeredAt = null;
    for (ServiceInstanceEntity instance : list) {
      capabilities.addAll(deserialize(instance.getCapabilitiesJson()));
      adminCapabilities.addAll(deserialize(instance.getAdminCapabilitiesJson()));
      if (registeredAt == null || instance.getRegisteredAt().isBefore(registeredAt)) {
        registeredAt = instance.getRegisteredAt();
      }
    }
    String health =
        instanceViews.stream().anyMatch(view -> "ACTIVE".equals(view.health()))
            ? "ACTIVE"
            : "STALE";
    return new RegistryViews.ServiceView(
        serviceId,
        health,
        instanceViews,
        List.copyOf(capabilities),
        List.copyOf(adminCapabilities),
        registeredAt);
  }

  String healthOf(Instant lastSeenAt) {
    return lastSeenAt.plusSeconds(staleAfterSeconds).isAfter(Instant.now()) ? "ACTIVE" : "STALE";
  }

  List<String> deserialize(String json) {
    try {
      return List.of(mapper.readValue(json, String[].class));
    } catch (JacksonException e) {
      throw new IllegalStateException("能力列表 JSON 损坏", e);
    }
  }
}
