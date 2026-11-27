package com.longzhong.jinguan.command;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** 命令仓储:领取按创建顺序,超时/清理按状态与时间。 */
public interface ControlCommandRepository extends JpaRepository<ControlCommandEntity, String> {

  Optional<ControlCommandEntity> findByIdempotencyKey(String idempotencyKey);

  List<ControlCommandEntity> findByServiceIdAndStatusOrderByCreatedAtAsc(
      String serviceId, String status, Limit limit);

  List<ControlCommandEntity> findByStatusAndClaimedAtBefore(String status, Instant cutoff);

  List<ControlCommandEntity> findByStatusInAndFinishedAtBefore(
      List<String> statuses, Instant cutoff);

  List<ControlCommandEntity> findAllByOrderByCreatedAtDesc(Limit limit);
}
