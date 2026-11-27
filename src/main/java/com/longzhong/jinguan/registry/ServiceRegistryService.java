package com.longzhong.jinguan.registry;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import com.longzhong.jinguan.common.ForbiddenException;
import com.longzhong.jinguan.common.NotFoundException;
import com.longzhong.jinguan.serviceidentity.ServicePrincipal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 服务注册/心跳与能力目录;超时只标 stale,不自动改服务状态。 */
@Service
public class ServiceRegistryService {

  private static final String INSTANCE_TARGET = "service-instance";
  private static final int MAX_FIELD_LENGTH = 128;
  private static final int MAX_CAPABILITIES = 500;

  private final ServiceRegistryRepository instances;
  private final RegistryViewAssembler views;
  private final AuditRecorder recorder;
  private final ObjectMapper mapper;

  public ServiceRegistryService(
      ServiceRegistryRepository instances,
      RegistryViewAssembler views,
      AuditRecorder recorder,
      ObjectMapper mapper) {
    this.instances = instances;
    this.views = views;
    this.recorder = recorder;
    this.mapper = mapper;
  }

  /** 注册/更新实例;payload 已由控制器做过字段白名单校验。 */
  @Transactional
  public RegistryViews.ServiceView register(ServicePrincipal principal, RegisterRequest request) {
    requireOwnService(principal, request.serviceId());
    List<String> capabilities = normalize(request.capabilities(), "capabilities");
    List<String> adminCapabilities = normalize(request.adminCapabilities(), "adminCapabilities");
    String capabilitiesJson = serialize(capabilities);
    String adminCapabilitiesJson = serialize(adminCapabilities);
    var existing =
        instances.findByServiceIdAndInstanceId(request.serviceId(), request.instanceId());
    ServiceInstanceEntity saved;
    if (existing.isPresent()) {
      ServiceInstanceEntity instance = existing.get();
      instance.refresh(
          request.environment(), request.version(), capabilitiesJson, adminCapabilitiesJson);
      saved = instances.save(instance);
    } else {
      saved =
          instances.save(
              ServiceInstanceEntity.register(
                  request.serviceId(),
                  request.instanceId(),
                  request.environment(),
                  request.version(),
                  capabilitiesJson,
                  adminCapabilitiesJson));
    }
    recorder.record(
        AuditActor.service(principal.serviceId()),
        AuditActions.SERVICE_REGISTER,
        INSTANCE_TARGET,
        request.serviceId() + "/" + request.instanceId(),
        null,
        null,
        Map.of(
            "serviceId", request.serviceId(),
            "instanceId", request.instanceId(),
            "environment", request.environment(),
            "version", request.version(),
            "capabilities", capabilities.size(),
            "adminCapabilities", adminCapabilities.size()),
        "SUCCESS");
    return serviceView(saved.getServiceId());
  }

  /** 心跳:只刷新 last_seen;未注册实例 404。 */
  @Transactional
  public RegistryViews.HeartbeatAck heartbeat(
      ServicePrincipal principal, String serviceId, String instanceId) {
    requireOwnService(principal, serviceId);
    if (instanceId == null || instanceId.isBlank()) {
      throw new IllegalArgumentException("instanceId 必填");
    }
    ServiceInstanceEntity instance =
        instances
            .findByServiceIdAndInstanceId(serviceId, instanceId)
            .orElseThrow(() -> new NotFoundException("实例未注册: " + serviceId + "/" + instanceId));
    instance.touch();
    ServiceInstanceEntity saved = instances.save(instance);
    return new RegistryViews.HeartbeatAck(
        saved.getServiceId(),
        saved.getInstanceId(),
        saved.getLastSeenAt(),
        views.healthOf(saved.getLastSeenAt()));
  }

  @Transactional(readOnly = true)
  public List<RegistryViews.ServiceView> services() {
    return views.services(instances.findAllByOrderByServiceIdAscInstanceIdAsc());
  }

  /** 权益/策略包可用键空间:所有实例能力的并集(含 stale,不因心跳丢失删除键)。 */
  @Transactional(readOnly = true)
  public Set<String> capabilityCatalog() {
    Set<String> catalog = new TreeSet<>();
    for (ServiceInstanceEntity instance : instances.findAll()) {
      catalog.addAll(views.deserialize(instance.getCapabilitiesJson()));
    }
    return catalog;
  }

  @Transactional
  public int purgeStale(Instant cutoff) {
    List<ServiceInstanceEntity> candidates = instances.findByLastSeenAtBefore(cutoff);
    if (candidates.isEmpty()) {
      return 0;
    }
    instances.deleteAll(candidates);
    recorder.record(
        AuditActor.system(),
        AuditActions.SERVICE_STALE_PURGE,
        INSTANCE_TARGET,
        null,
        null,
        "stale 保留期清理",
        Map.of("purged", candidates.size(), "cutoff", cutoff.toString()),
        "SUCCESS");
    return candidates.size();
  }

  private RegistryViews.ServiceView serviceView(String serviceId) {
    return views.viewOf(
        serviceId,
        instances.findAllByOrderByServiceIdAscInstanceIdAsc().stream()
            .filter(instance -> serviceId.equals(instance.getServiceId()))
            .toList());
  }

  private static void requireOwnService(ServicePrincipal principal, String serviceId) {
    if (serviceId == null || !serviceId.equals(principal.serviceId())) {
      throw new ForbiddenException("服务身份与目标服务不一致");
    }
  }

  private static List<String> normalize(List<String> values, String field) {
    if (values == null) {
      return List.of();
    }
    if (values.size() > MAX_CAPABILITIES) {
      throw new IllegalArgumentException(field + " 条目过多(上限 500)");
    }
    Set<String> unique = new LinkedHashSet<>();
    for (String value : values) {
      if (value == null || value.isBlank() || value.length() > MAX_FIELD_LENGTH) {
        throw new IllegalArgumentException(field + " 条目必须非空且不超过 128 字符");
      }
      unique.add(value.trim());
    }
    return List.copyOf(unique);
  }

  private String serialize(List<String> values) {
    try {
      return mapper.writeValueAsString(values);
    } catch (JacksonException e) {
      throw new IllegalStateException("能力列表序列化失败", e);
    }
  }

  /** 注册请求;字段白名单由控制器在反序列化前校验。 */
  public record RegisterRequest(
      String serviceId,
      String instanceId,
      String environment,
      String version,
      List<String> capabilities,
      List<String> adminCapabilities) {

    /** 复制能力列表,避免外部可变集合泄漏。 */
    public RegisterRequest {
      capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
      adminCapabilities = adminCapabilities == null ? List.of() : List.copyOf(adminCapabilities);
    }
  }
}
