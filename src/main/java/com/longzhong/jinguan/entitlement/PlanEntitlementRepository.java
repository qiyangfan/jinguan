package com.longzhong.jinguan.entitlement;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** 权益键仓储。 */
public interface PlanEntitlementRepository extends JpaRepository<PlanEntitlementEntity, String> {

  List<PlanEntitlementEntity> findByPlanIdOrderByEntitlementKeyAsc(String planId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from PlanEntitlementEntity e where e.planId = :planId")
  void deleteByPlanId(String planId);

  boolean existsByPlanId(String planId);
}
