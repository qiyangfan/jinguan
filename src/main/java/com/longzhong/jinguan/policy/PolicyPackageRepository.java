package com.longzhong.jinguan.policy;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 策略包仓储。 */
public interface PolicyPackageRepository extends JpaRepository<PolicyPackageEntity, String> {

  Optional<PolicyPackageEntity> findByPackageKey(String packageKey);

  boolean existsByPackageKey(String packageKey);
}
