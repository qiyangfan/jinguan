package com.longzhong.jinguan.saga;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 流程步骤仓储。 */
public interface SagaStepRepository extends JpaRepository<SagaStepEntity, String> {

  List<SagaStepEntity> findBySagaIdOrderBySeqAsc(String sagaId);

  void deleteBySagaId(String sagaId);
}
