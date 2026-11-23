package com.longzhong.jinguan.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 本地授权主体持久化端口；主键为Keycloak sub。 */
public interface PrincipalRepository extends JpaRepository<PrincipalEntity, String> {

  Optional<PrincipalEntity> findByUsername(String username);

  boolean existsByUsername(String username);

  /** 欠费停服置位(原子幂等):仅未停服者生效,返回实际置位数。 */
  @Modifying
  @Query(
      "UPDATE PrincipalEntity p SET p.suspendedAt = :at"
          + " WHERE p.subject = :subject AND p.suspendedAt IS NULL")
  int suspendIfClear(@Param("subject") String subject, @Param("at") Instant at);

  /** 停服恢复(原子幂等):仅停服者生效,返回实际恢复数。 */
  @Modifying
  @Query(
      "UPDATE PrincipalEntity p SET p.suspendedAt = NULL"
          + " WHERE p.subject = :subject AND p.suspendedAt IS NOT NULL")
  int resumeIfSuspended(@Param("subject") String subject);

  @Query("SELECT p.subject FROM PrincipalEntity p WHERE p.suspendedAt IS NOT NULL")
  List<String> findSuspendedSubjects();
}
