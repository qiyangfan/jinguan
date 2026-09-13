package com.longzhong.jinguan.wallet;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, Long> {

  Optional<LedgerEntryEntity> findByRefId(String refId);

  Page<LedgerEntryEntity> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

  @Query(
      "SELECT COALESCE(SUM(e.amountCredits), 0) FROM LedgerEntryEntity e WHERE e.userId = :userId")
  long sumByUserId(@Param("userId") Long userId);
}
