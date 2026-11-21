package com.longzhong.jinguan.oidc;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** OIDC 登录 state 持久化；消费必须原子。 */
public interface OidcLoginStateRepository extends JpaRepository<OidcLoginState, String> {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE OidcLoginState s SET s.consumedAt = :now"
          + " WHERE s.stateHash = :hash AND s.consumedAt IS NULL AND s.expiresAt > :now")
  int consume(@Param("hash") String hash, @Param("now") Instant now);

  @Modifying
  @Query("DELETE FROM OidcLoginState s WHERE s.expiresAt <= :now")
  int deleteExpired(@Param("now") Instant now);
}
