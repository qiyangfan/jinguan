package com.longzhong.jinguan.saga;

import com.longzhong.jinguan.alerts.AlertService;
import com.longzhong.jinguan.alerts.AlertTypes;
import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 流程终态/补偿/blocked 的审计与告警副作用,与引擎主流程解耦。 */
@Component
class SagaTransitions {

  static final String COMPLETED = "SUCCEEDED";
  static final String SUBMITTED = "SUBMITTED";
  static final String COMPENSATED = "COMPENSATED";

  private static final Logger log = LoggerFactory.getLogger(SagaTransitions.class);
  private static final String SAGA_TARGET = "saga";
  private static final String SAGA_ACTOR_PREFIX = "saga:";
  private static final String RESULT_FAILED = "FAILED";
  private static final String RESULT_SUCCESS = "SUCCESS";

  private final SagaTx tx;
  private final AuditRecorder recorder;
  private final AlertService alerts;

  SagaTransitions(SagaTx tx, AuditRecorder recorder, AlertService alerts) {
    this.tx = tx;
    this.recorder = recorder;
    this.alerts = alerts;
  }

  void finish(String sagaId, String status) {
    tx.updateSaga(sagaId, saga -> saga.transitionTo(status));
    recorder.record(
        AuditActor.user(SAGA_ACTOR_PREFIX + sagaId, null),
        SagaStatus.SUCCEEDED.equals(status)
            ? AuditActions.SAGA_COMPLETE
            : AuditActions.SAGA_COMPENSATE,
        SAGA_TARGET,
        sagaId,
        null,
        null,
        Map.of("status", status),
        RESULT_SUCCESS);
  }

  void startCompensation(String sagaId, String failure) {
    tx.updateSaga(sagaId, saga -> saga.transitionTo(SagaStatus.COMPENSATING));
    recorder.record(
        AuditActor.user(SAGA_ACTOR_PREFIX + sagaId, null),
        AuditActions.SAGA_COMPENSATE,
        SAGA_TARGET,
        sagaId,
        null,
        failure,
        Map.of("status", SagaStatus.COMPENSATING),
        RESULT_FAILED);
    log.warn("saga 进入补偿 id={} reason={}", sagaId, failure);
  }

  void block(ControlSagaEntity saga, SagaStepEntity step, String reason) {
    tx.updateStep(step.getId(), current -> current.markBlocked(reason));
    tx.updateSaga(saga.getId(), current -> current.transitionTo(SagaStatus.BLOCKED));
    recorder.record(
        AuditActor.user(SAGA_ACTOR_PREFIX + saga.getId(), null),
        AuditActions.SAGA_BLOCKED,
        SAGA_TARGET,
        saga.getId(),
        null,
        reason,
        Map.of("step", step.getName(), "status", SagaStatus.BLOCKED),
        RESULT_FAILED);
    alerts.raise(
        AlertTypes.SAGA_BLOCKED,
        "CRITICAL",
        SAGA_TARGET,
        null,
        null,
        SAGA_ACTOR_PREFIX + saga.getId(),
        Map.of("sagaId", saga.getId(), "type", saga.getSagaType(), "reason", reason),
        "docs/control-ha.md");
  }

  void auditStep(ControlSagaEntity saga, SagaStepEntity step, String outcome) {
    recorder.record(
        AuditActor.user(SAGA_ACTOR_PREFIX + saga.getId(), null),
        AuditActions.SAGA_STEP,
        "saga-step",
        saga.getId() + "/" + step.getSeq(),
        null,
        null,
        Map.of("name", step.getName(), "outcome", outcome),
        RESULT_FAILED.equals(outcome) ? RESULT_FAILED : RESULT_SUCCESS);
  }
}
