package com.longzhong.jinguan.lease;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** 租约行以悲观锁读写,避免两个实例同时判定为空闲。 */
public interface SchedulerLeaseRepository extends JpaRepository<SchedulerLeaseEntity, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<SchedulerLeaseEntity> findWithLockByName(String id);
}
