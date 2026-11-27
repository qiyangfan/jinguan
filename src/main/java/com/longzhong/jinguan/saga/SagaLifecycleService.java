package com.longzhong.jinguan.saga;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import com.longzhong.jinguan.common.ConflictException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 流程人工关闭与保留清理:未关闭 blocked 不删,审计不删。 */
@Service
public class SagaLifecycleService {

  private final ControlSagaRepository sagas;
  private final SagaTx tx;
  private final SagaQueryService query;
  private final AuditRecorder recorder;

  public SagaLifecycleService(
      ControlSagaRepository sagas, SagaTx tx, SagaQueryService query, AuditRecorder recorder) {
    this.sagas = sagas;
    this.tx = tx;
    this.query = query;
    this.recorder = recorder;
  }

  /** 人工关闭 blocked/failed 流程,保留到保留期后清理。 */
  public SagaViews.SagaView close(String id, String reason, String actor) {
    if (reason == null || reason.isBlank() || reason.length() > 512) {
      throw new IllegalArgumentException("变更原因必填且不超过 512 字符");
    }
    tx.updateSaga(
        id,
        saga -> {
          if (!SagaStatus.BLOCKED.equals(saga.getStatus())
              && !SagaStatus.FAILED.equals(saga.getStatus())) {
            throw new ConflictException("只有 blocked/failed 流程可人工关闭");
          }
          saga.close(reason);
        });
    recorder.record(
        AuditActor.user(actor, null),
        AuditActions.SAGA_CLOSE,
        "saga",
        id,
        null,
        reason,
        Map.of("action", "close"),
        "SUCCESS");
    return query.view(id);
  }

  /** 清理终态流程与已关闭 blocked;未关闭 blocked 永不删除。 */
  public int purge(Instant cutoff) {
    List<ControlSagaEntity> purge = new ArrayList<>();
    purge.addAll(
        sagas.findByStatusInAndUpdatedAtBefore(
            List.of(SagaStatus.SUCCEEDED, SagaStatus.COMPENSATED, SagaStatus.FAILED), cutoff));
    purge.addAll(sagas.findByStatusAndClosedAtBefore(SagaStatus.BLOCKED, cutoff));
    if (purge.isEmpty()) {
      return 0;
    }
    for (ControlSagaEntity saga : purge) {
      tx.purge(saga.getId());
    }
    recorder.record(
        AuditActor.system(),
        AuditActions.SAGA_PURGE,
        "saga",
        null,
        null,
        "流程保留期清理",
        Map.of("purged", purge.size(), "cutoff", cutoff.toString()),
        "SUCCESS");
    return purge.size();
  }
}
