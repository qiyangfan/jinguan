package com.longzhong.jinguan.tenant;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 租户成员仓储。 */
public interface TenantMemberRepository extends JpaRepository<TenantMemberEntity, String> {

  List<TenantMemberEntity> findByTenantIdOrderBySubjectAsc(String tenantId);

  List<TenantMemberEntity> findAllByOrderByTenantIdAscSubjectAsc();

  Optional<TenantMemberEntity> findByTenantIdAndSubject(String tenantId, String subject);

  boolean existsByTenantIdAndSubject(String tenantId, String subject);
}
