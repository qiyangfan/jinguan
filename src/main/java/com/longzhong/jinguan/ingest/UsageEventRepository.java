package com.longzhong.jinguan.ingest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** 用量事件仓储。 */
public interface UsageEventRepository extends JpaRepository<UsageEventEntity, String> {

  boolean existsBySourceAndEventIdAndMetric(String source, String eventId, String metric);

  List<UsageEventEntity> findByAggregatedAtIsNullOrderByOccurredAtAsc(Limit limit);

  List<UsageEventEntity> findByAggregatedAtIsNotNullAndOccurredAtBefore(Instant cutoff);

  List<UsageEventEntity> findAllByOrderByOccurredAtDesc(Limit limit);

  Optional<UsageEventEntity> findFirstByOrderByOccurredAtDesc();
}
