package com.longzhong.jinguan.saga;

import com.longzhong.jinguan.common.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 流程状态事务边界:所有写操作在独立事务内提交,失败补偿不回滚已完成步骤。 */
@Component
class SagaTx {

  private final ControlSagaRepository sagas;
  private final SagaStepRepository steps;
  private final TransactionTemplate tx;

  SagaTx(
      ControlSagaRepository sagas,
      SagaStepRepository steps,
      PlatformTransactionManager transactionManager) {
    this.sagas = sagas;
    this.steps = steps;
    this.tx = new TransactionTemplate(transactionManager);
  }

  /** 读取一致快照(实体在事务内加载,字段均为即时值)。 */
  Snapshot read(String id) {
    return tx.execute(
        status ->
            new Snapshot(
                sagas.findById(id).orElseThrow(() -> new NotFoundException("流程不存在: " + id)),
                steps.findBySagaIdOrderBySeqAsc(id)));
  }

  Optional<ControlSagaEntity> findByIdempotencyKey(String key) {
    return tx.execute(status -> sagas.findByIdempotencyKey(key));
  }

  void create(ControlSagaEntity saga, List<SagaStepEntity> sagaSteps) {
    tx.executeWithoutResult(
        status -> {
          sagas.save(saga);
          steps.saveAll(sagaSteps);
        });
  }

  void updateSaga(String id, Consumer<ControlSagaEntity> mutation) {
    tx.executeWithoutResult(
        status -> {
          ControlSagaEntity saga =
              sagas.findById(id).orElseThrow(() -> new NotFoundException("流程不存在: " + id));
          mutation.accept(saga);
          sagas.save(saga);
        });
  }

  void updateStep(String id, Consumer<SagaStepEntity> mutation) {
    tx.executeWithoutResult(
        status -> {
          SagaStepEntity step =
              steps.findById(id).orElseThrow(() -> new NotFoundException("流程步骤不存在: " + id));
          mutation.accept(step);
          steps.save(step);
        });
  }

  void purge(String id) {
    tx.executeWithoutResult(
        status -> {
          steps.deleteBySagaId(id);
          sagas.deleteById(id);
        });
  }

  /** 流程与步骤的一致性读快照。 */
  record Snapshot(ControlSagaEntity saga, List<SagaStepEntity> steps) {}
}
