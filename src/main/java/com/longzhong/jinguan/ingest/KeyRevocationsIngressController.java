package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.common.CommittedMetrics;
import com.longzhong.jinguan.common.MetricNames;
import com.longzhong.jinguan.user.SuspensionService;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 匿名下发 key 吊销全量快照；muniu 按单调 version 应用，名单包含终态吊销和停服用户的绑定 key。 */
@RestController
public class KeyRevocationsIngressController {

  private final KeyRevocationRepository revocations;
  private final ApiKeyBindingRepository bindings;
  private final SuspensionService suspension;
  private final CommittedMetrics metrics;

  public KeyRevocationsIngressController(
      KeyRevocationRepository revocations,
      ApiKeyBindingRepository bindings,
      SuspensionService suspension,
      CommittedMetrics metrics) {
    this.revocations = revocations;
    this.bindings = bindings;
    this.suspension = suspension;
    this.metrics = metrics;
  }

  @GetMapping("/control/v1/key-revocations")
  public Map<String, Object> revocations() {
    // 先读版本再读名单，避免返回新版本却携带旧名单。
    long version = suspension.keyRevocationVersion();
    TreeSet<String> revoked = new TreeSet<>(revocations.findAllKeyIds());
    List<Long> suspended = suspension.suspendedUserIds();
    if (!suspended.isEmpty()) {
      bindings.findByUserIdIn(suspended).forEach(b -> revoked.add(b.getKeyId()));
    }
    // 成包后再计数，读库失败时不记为成功拉取。
    metrics.count(
        MetricNames.CONTROL_PULL,
        MetricNames.TAG_PIPE,
        MetricNames.PIPE_KEY_REVOCATIONS,
        MetricNames.TAG_OUTCOME,
        MetricNames.OUTCOME_FULL);
    return Map.of("version", version, "revoked", List.copyOf(revoked));
  }
}
