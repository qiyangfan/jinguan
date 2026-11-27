package com.longzhong.jinguan.saga;

import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** 流程与步骤视图;敏感值不入 context。 */
public interface SagaViews {

  /** 流程步骤视图。 */
  record StepView(
      int seq,
      String name,
      String kind,
      String action,
      String status,
      String commandId,
      int attempts,
      String error,
      JsonNode params,
      Instant startedAt,
      Instant finishedAt) {}

  /** 流程视图(含步骤)。 */
  record SagaView(
      String id,
      String sagaType,
      String idempotencyKey,
      String status,
      String reason,
      String actor,
      JsonNode context,
      Instant createdAt,
      Instant updatedAt,
      Instant closedAt,
      String closeReason,
      List<StepView> steps) {

    public SagaView {
      steps = List.copyOf(steps);
    }
  }
}
