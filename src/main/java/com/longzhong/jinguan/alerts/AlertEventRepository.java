package com.longzhong.jinguan.alerts;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** 告警事件读写;投递任务只取 PENDING,列表供控制台查询。 */
public interface AlertEventRepository extends JpaRepository<AlertEventEntity, String> {

  boolean existsByDedupKeyAndCreatedAtAfter(String dedupKey, Instant after);

  List<AlertEventEntity> findByStatusOrderByCreatedAtAsc(String status, Limit limit);

  List<AlertEventEntity> findAllByOrderByCreatedAtDesc(Limit limit);

  List<AlertEventEntity> findByStatusInAndCreatedAtBefore(List<String> statuses, Instant cutoff);
}
