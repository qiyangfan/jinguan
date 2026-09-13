package com.longzhong.jinguan.payment;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

  /**
   * 支付状态原子迁移(CREATED → PAID):以订单行锁串行化并发回调——败方 updated=0 走幂等重放, 不会产生并发同 refId 的重复入账事件(订单行锁即支付路径的
   * per-key 锁)。clearAutomatically 清理持久化上下文,保证败方重读看到提交后状态。
   */
  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE MockOrderEntity o SET o.status = 'PAID', o.paidAt = :paidAt WHERE o.orderId = :orderId AND o.status = 'CREATED'")
  int markPaidIfCreated(@Param("orderId") String orderId, @Param("paidAt") Instant paidAt);

  /**
   * 过期收敛原子迁移(CREATED → EXPIRED):与回调路径(markPaidIfCreated)对称的 CAS—— 带 status 谓词,杜绝扫描脏检查无条件覆盖把并发已 PAID
   * 订单翻成 EXPIRED 的丢失更新。 clearAutomatically 清理持久化上下文,调用方重读可见提交后状态。
   */
  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE MockOrderEntity o SET o.status = 'EXPIRED' WHERE o.orderId IN :orderIds AND o.status = 'CREATED'")
  int expireIfCreated(@Param("orderIds") List<String> orderIds);

  /** 本人订单簿(新→旧;orderId 含时间前缀,字典序即时序)。 */
  Page<MockOrderEntity> findByUserIdOrderByOrderIdDesc(long userId, Pageable pageable);
}
