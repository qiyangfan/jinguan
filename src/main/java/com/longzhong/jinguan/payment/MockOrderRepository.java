package com.longzhong.jinguan.payment;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MockOrderRepository extends JpaRepository<MockOrderEntity, String> {

  /**
   * 过期收敛候选:keyset 批量取最早过期的 CREATED 订单(单轮上限由 Pageable 控制, 防积压场景单轮锁死;剩余留给下轮 fixedDelay 自然节流)。JPA 形态保持
   * H2/PG 双兼容。
   */
  @Query(
      "SELECT o FROM MockOrderEntity o WHERE o.status = :status AND o.expiresAt < :before ORDER BY o.id ASC")
  List<MockOrderEntity> findOverdue(
      @Param("status") String status, @Param("before") Instant before, Pageable pageable);

  /** 积压深度:已过期仍未收敛的存量(可观测指标,扫描任务每轮上报)。 */
  long countByStatusAndExpiresAtBefore(String status, Instant before);
}
