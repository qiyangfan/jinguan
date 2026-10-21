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

  /** 分页查询已过期且仍为 CREATED 的订单，按订单号升序。 */
  @Query(
      "SELECT o FROM MockOrderEntity o WHERE o.status = :status AND o.expiresAt < :before ORDER BY o.id ASC")
  List<MockOrderEntity> findOverdue(
      @Param("status") String status, @Param("before") Instant before, Pageable pageable);

  /** 统计已过期但仍为 CREATED 的订单数。 */
  long countByStatusAndExpiresAtBefore(String status, Instant before);

  /** 原子地将 CREATED 订单改为 PAID，防止并发回调重复处理。 */
  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE MockOrderEntity o SET o.status = 'PAID', o.paidAt = :paidAt WHERE o.orderId = :orderId AND o.status = 'CREATED'")
  int markPaidIfCreated(@Param("orderId") String orderId, @Param("paidAt") Instant paidAt);

  /** 原子地将 CREATED 订单改为 EXPIRED，避免覆盖并发支付。 */
  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE MockOrderEntity o SET o.status = 'EXPIRED' WHERE o.orderId IN :orderIds AND o.status = 'CREATED'")
  int expireIfCreated(@Param("orderIds") List<String> orderIds);

  /** 按订单号倒序分页查询用户订单。 */
  Page<MockOrderEntity> findByUserIdOrderByOrderIdDesc(long userId, Pageable pageable);
}
