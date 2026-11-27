package com.longzhong.jinguan.entitlement;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 租户套餐绑定仓储。 */
public interface TenantPlanBindingRepository
    extends JpaRepository<TenantPlanBindingEntity, String> {

  Optional<TenantPlanBindingEntity> findByTenantId(String tenantId);

  List<TenantPlanBindingEntity> findAllByOrderByTenantIdAsc();

  List<TenantPlanBindingEntity> findByPlanIdAndStatus(String planId, String status);
}
