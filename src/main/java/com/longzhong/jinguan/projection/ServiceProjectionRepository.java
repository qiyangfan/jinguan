package com.longzhong.jinguan.projection;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 投影缓存仓储。 */
public interface ServiceProjectionRepository
    extends JpaRepository<ServiceProjectionEntity, String> {

  List<ServiceProjectionEntity> findByFetchedAtBefore(Instant cutoff);
}
