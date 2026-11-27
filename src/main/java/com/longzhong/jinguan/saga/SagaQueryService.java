package com.longzhong.jinguan.saga;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 流程查询:视图/列表/待推进 id;敏感值不入上下文。 */
@Service
public class SagaQueryService {

  private final ControlSagaRepository sagas;
  private final SagaTx tx;
  private final ObjectMapper mapper;

  public SagaQueryService(ControlSagaRepository sagas, SagaTx tx, ObjectMapper mapper) {
    this.sagas = sagas;
    this.tx = tx;
    this.mapper = mapper;
  }

  public SagaViews.SagaView view(String id) {
    SagaTx.Snapshot snapshot = tx.read(id);
    ControlSagaEntity saga = snapshot.saga();
    List<SagaViews.StepView> stepViews =
        snapshot.steps().stream()
            .map(
                step ->
                    new SagaViews.StepView(
                        step.getSeq(),
                        step.getName(),
                        step.getKind(),
                        step.getAction(),
                        step.getStatus(),
                        step.getCommandId(),
                        step.getAttempts(),
                        step.getError(),
                        parse(step.getParamsJson()),
                        step.getStartedAt(),
                        step.getFinishedAt()))
            .toList();
    return new SagaViews.SagaView(
        saga.getId(),
        saga.getSagaType(),
        saga.getIdempotencyKey(),
        saga.getStatus(),
        saga.getReason(),
        saga.getActor(),
        parse(saga.getContextJson()),
        saga.getCreatedAt(),
        saga.getUpdatedAt(),
        saga.getClosedAt(),
        saga.getCloseReason(),
        stepViews);
  }

  public List<SagaViews.SagaView> list(String status, int limit) {
    int safeLimit = Math.min(Math.max(limit, 1), 200);
    return sagas.findAllByOrderByCreatedAtDesc(Limit.of(300)).stream()
        .filter(s -> status == null || status.isBlank() || status.equalsIgnoreCase(s.getStatus()))
        .limit(safeLimit)
        .map(s -> view(s.getId()))
        .toList();
  }

  /** 待推进的流程 id:pending/running/compensating。 */
  public List<String> pendingIds() {
    return sagas
        .findByStatusInOrderByUpdatedAtAsc(
            List.of(SagaStatus.PENDING, SagaStatus.RUNNING, SagaStatus.COMPENSATING), Limit.of(50))
        .stream()
        .map(ControlSagaEntity::getId)
        .toList();
  }

  private JsonNode parse(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return mapper.readTree(json);
    } catch (RuntimeException e) {
      return null;
    }
  }
}
