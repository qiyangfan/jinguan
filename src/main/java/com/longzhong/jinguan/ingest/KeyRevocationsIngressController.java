package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.common.CommittedMetrics;
import com.longzhong.jinguan.common.MetricNames;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 匿名下发 key 吊销全量快照；muniu 按单调 version 应用，名单包含终态吊销和停服用户的绑定 key。 */
@RestController
public class KeyRevocationsIngressController {

  private final KeyRevocationQueryService queries;
  private final CommittedMetrics metrics;

  public KeyRevocationsIngressController(
      KeyRevocationQueryService queries, CommittedMetrics metrics) {
    this.queries = queries;
    this.metrics = metrics;
  }

  @GetMapping("/control/v1/key-revocations")
  public Map<String, Object> revocations() {
    KeyRevocationQueryService.Snapshot snapshot = queries.snapshot();
    // 成包后再计数，读库失败时不记为成功拉取。
    metrics.count(
        MetricNames.CONTROL_PULL,
        MetricNames.TAG_PIPE,
        MetricNames.PIPE_KEY_REVOCATIONS,
        MetricNames.TAG_OUTCOME,
        MetricNames.OUTCOME_FULL);
    return Map.of("version", snapshot.version(), "revoked", snapshot.revoked());
  }
}
