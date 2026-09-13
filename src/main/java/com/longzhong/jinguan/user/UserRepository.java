package com.longzhong.jinguan.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByUsername(String username);

  boolean existsByUsername(String username);

  /** 欠费停服置位(原子幂等):仅未停服者生效,返回实际置位数。 */
  @Modifying
  @Query("UPDATE UserEntity u SET u.suspendedAt = :at WHERE u.id = :id AND u.suspendedAt IS NULL")
  int suspendIfClear(@Param("id") long id, @Param("at") Instant at);

  /** 停服恢复(原子幂等):仅停服者生效,返回实际恢复数。 */
  @Modifying
  @Query(
      "UPDATE UserEntity u SET u.suspendedAt = NULL WHERE u.id = :id AND u.suspendedAt IS NOT NULL")
  int resumeIfSuspended(@Param("id") long id);

  @Query("SELECT u.id FROM UserEntity u WHERE u.suspendedAt IS NOT NULL")
  List<Long> findSuspendedIds();
}
