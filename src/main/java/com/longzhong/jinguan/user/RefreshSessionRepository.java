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
  @Query("UPDATE RefreshSession r SET r.consumed = true WHERE r.userId = :uid")
  int revokeAllByUser(@Param("uid") long uid);
}
