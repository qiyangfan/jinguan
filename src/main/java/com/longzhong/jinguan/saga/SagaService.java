package com.longzhong.jinguan.saga;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import com.longzhong.jinguan.command.CommandQueryService;
import com.longzhong.jinguan.command.CommandService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 跨域流程引擎:管理步骤写管理数据,服务步骤发声明式命令;失败逆序补偿,补偿失败进入 blocked 并告警;不使用分布式事务/跨库锁。 */
@Service
public class SagaService {

  private static final Logger log = LoggerFactory.getLogger(SagaService.class);
  private static final String SAGA_TARGET = "saga";
  private static final String SAGA_ACTOR_PREFIX = "saga:";

  private final SagaTx tx;
  private final SagaQueryService query;
  private final SagaAdminSteps adminSteps;
  private final SagaTransitions transitions;
  private final CommandService commands;
  private final CommandQueryService commandQueries;
  private final SagaContextCodec codec;
  private final AuditRecorder recorder;
  private final int stepTimeoutSeconds;

  public SagaService(
      SagaTx tx,
      SagaQueryService query,
      SagaAdminSteps adminSteps,
      SagaTransitions transitions,
      CommandService commands,
      CommandQueryService commandQueries,
      SagaContextCodec codec,
      AuditRecorder recorder,
      @Value("${jinguan.sagas.step-timeout-seconds:600}") int stepTimeoutSeconds) {
    this.tx = tx;
    this.query = query;
    this.adminSteps = adminSteps;
    this.transitions = transitions;
    this.commands = commands;
    this.commandQueries = commandQueries;
    this.codec = codec;
    this.recorder = recorder;
    this.stepTimeoutSeconds = stepTimeoutSeconds;
  }

  /** 启动流程:幂等键复用;创建后立即推进到可执行的最远状态。 */
  @SuppressWarnings("PMD.UseObjectForClearerAPI") // 启动参数为独立必填字段,容器对象只增样板
  public SagaViews.SagaView start(
      String type,
      String idempotencyKey,
      Map<String, Object> params,
      String reason,
      boolean confirmed,
      String actor) {
    SagaValidation.StartParams validated =
        SagaValidation.validate(type, idempotencyKey, params, reason, confirmed);
    String key =
        validated.idempotencyKey() == null
            ? UUID.randomUUID().toString()
            : validated.idempotencyKey();
    var existing = tx.findByIdempotencyKey(key);
    if (existing.isPresent()) {
      return query.view(existing.get().getId());
    }
    Map<String, Object> context = new LinkedHashMap<>();
    context.put(SagaStatus.CONTEXT_TENANT_KEY, validated.tenantKey());
    context.put(SagaStatus.CONTEXT_NAME, validated.name());
    context.put(SagaStatus.CONTEXT_PLAN_KEY, validated.planKey());
    ControlSagaEntity saga =
        ControlSagaEntity.start(validated.type(), key, codec.serialize(context), reason, actor);
    List<SagaStepEntity> steps =
        List.of(
            SagaStepEntity.define(
                saga.getId(),
                1,
                SagaStatus.STEP_CREATE_TENANT,
                SagaStatus.STEP_ADMIN,
                null,
                null,
                "{}"),
            SagaStepEntity.define(
                saga.getId(),
                2,
                SagaStatus.STEP_BIND_PLAN,
                SagaStatus.STEP_ADMIN,
                null,
                null,
                "{}"),
            SagaStepEntity.define(
                saga.getId(),
                3,
                SagaStatus.STEP_TENANT_PROVISION,
                SagaStatus.STEP_SERVICE,
                SagaStatus.ACTION_TENANT_PROVISION,
                1,
                codec.serialize(Map.of(SagaStatus.PARAM_TENANT_KEY, validated.tenantKey()))),
            SagaStepEntity.define(
                saga.getId(),
                4,
                SagaStatus.STEP_PLAN_APPLY,
                SagaStatus.STEP_SERVICE,
                SagaStatus.ACTION_PLAN_APPLY,
                1,
                codec.serialize(
                    Map.of(
                        SagaStatus.PARAM_TENANT_KEY, validated.tenantKey(),
                        SagaStatus.PARAM_PLAN_KEY, validated.planKey()))));
    try {
      tx.create(saga, steps);
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      return query.view(
          tx.findByIdempotencyKey(key)
              .orElseThrow(() -> new IllegalStateException("流程幂等冲突", e))
              .getId());
    }
    recorder.record(
        AuditActor.user(actor == null ? SAGA_TARGET : actor, null),
        AuditActions.SAGA_START,
        SAGA_TARGET,
        saga.getId(),
        validated.tenantKey(),
        reason,
        Map.of("type", validated.type(), "idempotencyKey", key),
        "SUCCESS");
    return advance(saga.getId());
  }

  /** 推进状态机;遇到等待(服务命令未回执)或终态即返回。 */
  public SagaViews.SagaView advance(String id) {
    for (int guard = 0; guard < 32; guard++) {
      SagaTx.Snapshot snapshot = tx.read(id);
      String status = snapshot.saga().getStatus();
      if (SagaRules.isTerminal(status)) {
        return query.view(id);
      }
      boolean progressed =
          switch (status) {
            case SagaStatus.PENDING -> {
              tx.updateSaga(id, saga -> saga.transitionTo(SagaStatus.RUNNING));
              yield true;
            }
            case SagaStatus.RUNNING -> advanceRunning(snapshot);
            case SagaStatus.COMPENSATING -> advanceCompensation(snapshot);
            default -> false;
          };
      if (!progressed) {
        return query.view(id);
      }
    }
    log.warn("saga 推进轮次超限 id={}", id);
    return query.view(id);
  }

  private boolean advanceRunning(SagaTx.Snapshot snapshot) {
    SagaStepEntity next = null;
    for (SagaStepEntity step : snapshot.steps()) {
      if (!SagaRules.isStepDone(step.getStatus())) {
        next = step;
        break;
      }
    }
    if (next == null) {
      transitions.finish(snapshot.saga().getId(), SagaStatus.SUCCEEDED);
      return false;
    }
    Map<String, Object> context = codec.parse(snapshot.saga().getContextJson());
    boolean isAdmin = SagaStatus.STEP_ADMIN.equals(next.getKind());
    if (SagaStatus.PENDING.equals(next.getStatus())) {
      return isAdmin
          ? executeAdminStep(snapshot.saga(), next, context)
          : submitServiceStep(snapshot.saga(), next);
    }
    if (isAdmin) {
      transitions.block(snapshot.saga(), next, "管理步骤长时间未完成,需人工核查");
      return false;
    }
    return awaitServiceStep(snapshot.saga(), next);
  }

  private boolean executeAdminStep(
      ControlSagaEntity saga, SagaStepEntity step, Map<String, Object> context) {
    tx.updateStep(step.getId(), SagaStepEntity::markRunning);
    try {
      adminSteps.execute(saga, step, context);
    } catch (RuntimeException e) {
      tx.updateStep(step.getId(), current -> current.markFailed(e.getMessage()));
      transitions.startCompensation(saga.getId(), "管理步骤失败: " + e.getMessage());
      return true;
    }
    tx.updateStep(step.getId(), SagaStepEntity::markSucceeded);
    tx.updateSaga(saga.getId(), current -> current.updateContext(codec.serialize(context)));
    transitions.auditStep(saga, step, SagaTransitions.COMPLETED);
    return true;
  }

  private boolean submitServiceStep(ControlSagaEntity saga, SagaStepEntity step) {
    CommandService.SubmitResult submit =
        commands.submit(
            SagaRules.serviceIdOf(step.getAction()),
            step.getAction(),
            step.getActionSchemaVersion(),
            codec.parseNode(step.getParamsJson()),
            "saga " + saga.getSagaType() + " 步骤 " + step.getName(),
            SAGA_ACTOR_PREFIX + saga.getId(),
            SAGA_ACTOR_PREFIX + saga.getId() + ":step:" + step.getSeq());
    tx.updateStep(
        step.getId(),
        current -> {
          current.attachCommand(submit.command().id());
          current.markRunning();
        });
    transitions.auditStep(saga, step, SagaTransitions.SUBMITTED);
    return true;
  }

  private boolean awaitServiceStep(ControlSagaEntity saga, SagaStepEntity step) {
    CommandService.CommandView command = commandQueries.get(step.getCommandId());
    String status = command.status();
    if (SagaTransitions.COMPLETED.equals(status)) {
      tx.updateStep(step.getId(), SagaStepEntity::markSucceeded);
      transitions.auditStep(saga, step, SagaTransitions.COMPLETED);
      return true;
    }
    if (SagaRules.isCommandFailed(status)) {
      String failure = command.error() == null ? "服务执行失败" : command.error();
      tx.updateStep(step.getId(), current -> current.markFailed(failure));
      transitions.startCompensation(saga.getId(), "服务命令失败: " + status);
      return true;
    }
    if (SagaRules.timedOut(step, stepTimeoutSeconds)) {
      transitions.block(saga, step, "服务命令等待超时,流程停在 blocked");
    }
    return false;
  }

  private boolean advanceCompensation(SagaTx.Snapshot snapshot) {
    ControlSagaEntity saga = snapshot.saga();
    Map<String, Object> context = codec.parse(saga.getContextJson());
    List<SagaStepEntity> reversed = new ArrayList<>(snapshot.steps());
    reversed.sort((a, b) -> Integer.compare(b.getSeq(), a.getSeq()));
    for (SagaStepEntity step : reversed) {
      if (SagaStatus.SUCCEEDED.equals(step.getStatus())) {
        boolean compensated =
            SagaStatus.STEP_ADMIN.equals(step.getKind())
                ? compensateAdminStep(saga, step, context)
                : compensateServiceStep(saga, step, context);
        if (!compensated) {
          return false;
        }
      }
    }
    transitions.finish(saga.getId(), SagaStatus.COMPENSATED);
    return false;
  }

  private boolean compensateAdminStep(
      ControlSagaEntity saga, SagaStepEntity step, Map<String, Object> context) {
    try {
      adminSteps.compensate(saga, step, context);
    } catch (RuntimeException e) {
      transitions.block(saga, step, "管理补偿失败: " + e.getMessage());
      return false;
    }
    tx.updateStep(step.getId(), SagaStepEntity::markCompensated);
    transitions.auditStep(saga, step, SagaTransitions.COMPENSATED);
    return true;
  }

  private boolean compensateServiceStep(
      ControlSagaEntity saga, SagaStepEntity step, Map<String, Object> context) {
    String compensationAction = SagaRules.compensationActionOf(step.getAction());
    if (compensationAction == null) {
      transitions.block(saga, step, "服务步骤缺少补偿动作: " + step.getAction());
      return false;
    }
    String contextKey = SagaStatus.CONTEXT_COMPENSATION_PREFIX + step.getSeq();
    String compensationCommandId = (String) context.get(contextKey);
    if (compensationCommandId == null) {
      CommandService.SubmitResult submit =
          commands.submit(
              SagaRules.serviceIdOf(step.getAction()),
              compensationAction,
              1,
              SagaRules.compensationParams(codec, context),
              "saga 补偿 " + step.getAction(),
              SAGA_ACTOR_PREFIX + saga.getId(),
              SAGA_ACTOR_PREFIX + saga.getId() + ":comp:" + step.getSeq());
      tx.updateSaga(
          saga.getId(),
          current -> {
            Map<String, Object> next = codec.parse(current.getContextJson());
            next.put(contextKey, submit.command().id());
            current.updateContext(codec.serialize(next));
          });
      return false;
    }
    CommandService.CommandView command = commandQueries.get(compensationCommandId);
    if (SagaTransitions.COMPLETED.equals(command.status())) {
      tx.updateStep(step.getId(), SagaStepEntity::markCompensated);
      transitions.auditStep(saga, step, SagaTransitions.COMPENSATED);
      return true;
    }
    if (SagaRules.isCommandFailed(command.status())
        || SagaRules.timedOut(step, stepTimeoutSeconds)) {
      transitions.block(saga, step, "补偿命令失败或超时: " + command.error());
    }
    return false;
  }
}
