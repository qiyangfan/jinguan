package com.longzhong.jinguan.tenant;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 租户仓储。 */
public interface TenantRepository extends JpaRepository<TenantEntity, String> {

  Optional<TenantEntity> findByTenantKey(String tenantKey);

  boolean existsByTenantKey(String tenantKey);
}
