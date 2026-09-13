package com.longzhong.jinguan.ingest;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageRecordRepository extends JpaRepository<UsageRecordEntity, Long> {

  Optional<UsageRecordEntity> findByUsageId(String usageId);

  Page<UsageRecordEntity> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
}
