package com.longzhong.jinguan.saga;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** 流程仓储。 */
public interface ControlSagaRepository extends JpaRepository<ControlSagaEntity, String> {

  Optional<ControlSagaEntity> findByIdempotencyKey(String idempotencyKey);

  List<ControlSagaEntity> findByStatusInOrderByUpdatedAtAsc(List<String> statuses, Limit limit);

  List<ControlSagaEntity> findByStatusInAndUpdatedAtBefore(List<String> statuses, Instant cutoff);

  List<ControlSagaEntity> findByStatusAndClosedAtBefore(String status, Instant cutoff);

  List<ControlSagaEntity> findAllByOrderByCreatedAtDesc(Limit limit);
}
