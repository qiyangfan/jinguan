package com.longzhong.jinguan.registry;

import java.time.Instant;
import java.util.List;

/** 注册表只读视图:健康由 last_seen 推导,不伪造。 */
public interface RegistryViews {

  /** 服务实例视图。 */
  record InstanceView(
      String instanceId, String environment, String version, Instant lastSeenAt, String health) {}

  /** 服务视图(实例/能力/健康)。 */
  record ServiceView(
      String serviceId,
      String health,
      List<InstanceView> instances,
      List<String> capabilities,
      List<String> adminCapabilities,
      Instant registeredAt) {

    public ServiceView {
      instances = List.copyOf(instances);
      capabilities = List.copyOf(capabilities);
      adminCapabilities = List.copyOf(adminCapabilities);
    }
  }

  /** 心跳确认。 */
  record HeartbeatAck(String serviceId, String instanceId, Instant lastSeenAt, String health) {}
}
