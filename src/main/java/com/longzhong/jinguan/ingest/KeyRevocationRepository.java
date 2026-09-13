package com.longzhong.jinguan.ingest;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface KeyRevocationRepository extends JpaRepository<KeyRevocationEntity, Long> {

  boolean existsByKeyId(String keyId);

  /** 全量快照用:当前全部终态吊销 key(排序保确定性,便于契约测试逐字段断言)。 */
  @Query("SELECT r.keyId FROM KeyRevocationEntity r ORDER BY r.keyId")
  List<String> findAllKeyIds();
}
