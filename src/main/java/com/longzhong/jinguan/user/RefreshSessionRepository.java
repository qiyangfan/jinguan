package com.longzhong.jinguan.user;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** refresh 会话持久化端口。 */
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, String> {

  /** 原子消费一枚活跃 refresh 票并记录轮换时刻;返回 1 表示本次轮换成功。 */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE RefreshSession r SET r.consumed = true, r.rotatedAt = :at"
          + " WHERE r.jti = :jti AND r.consumed = false")
  int consume(@Param("jti") String jti, @Param("at") Instant at);

  /** 吊销某用户全部 refresh 会话(置 consumed);用于重放、登出与改密。 */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE RefreshSession r SET r.consumed = true WHERE r.subject = :subject")
  int revokeAllByUser(@Param("subject") String subject);

  /** 删除已过期的 refresh 会话行;过期票本就不可用,删除不影响任何校验。 */
  @Modifying
  @Query("DELETE FROM RefreshSession r WHERE r.expiresAt <= :now")
  int deleteExpired(@Param("now") Instant now);
}
