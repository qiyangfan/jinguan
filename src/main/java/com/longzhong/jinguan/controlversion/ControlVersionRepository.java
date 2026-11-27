package com.longzhong.jinguan.controlversion;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** 版本水位只有单行,更新前取悲观锁保证并发写不丢递增。 */
public interface ControlVersionRepository extends JpaRepository<ControlVersionEntity, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<ControlVersionEntity> findWithLockById(String id);
}
