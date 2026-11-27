package com.longzhong.jinguan.registry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 服务实例仓储。 */
public interface ServiceRegistryRepository extends JpaRepository<ServiceInstanceEntity, String> {

  Optional<ServiceInstanceEntity> findByServiceIdAndInstanceId(String serviceId, String instanceId);

  List<ServiceInstanceEntity> findAllByOrderByServiceIdAscInstanceIdAsc();

  List<ServiceInstanceEntity> findByLastSeenAtBefore(Instant cutoff);
}
