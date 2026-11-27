package com.longzhong.jinguan.entitlement;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** 配额维度仓储。 */
public interface PlanQuotaRepository extends JpaRepository<PlanQuotaEntity, String> {

  List<PlanQuotaEntity> findByPlanIdOrderByDimensionAsc(String planId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from PlanQuotaEntity q where q.planId = :planId")
  void deleteByPlanId(String planId);
}
