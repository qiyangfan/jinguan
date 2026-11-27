package com.longzhong.jinguan.entitlement;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 套餐仓储。 */
public interface PlanRepository extends JpaRepository<PlanEntity, String> {

  Optional<PlanEntity> findByPlanKey(String planKey);

  boolean existsByPlanKey(String planKey);
}
