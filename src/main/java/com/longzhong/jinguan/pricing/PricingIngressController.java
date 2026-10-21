package com.longzhong.jinguan.pricing;

import com.longzhong.jinguan.common.CommittedMetrics;
import com.longzhong.jinguan.common.MetricNames;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** muniu 通过此端点拉取价目；响应字段与数据面契约一致。 */
@RestController
public class PricingIngressController {

  private final PricingService pricing;
  private final CommittedMetrics metrics;

  public PricingIngressController(PricingService pricing, CommittedMetrics metrics) {
    this.pricing = pricing;
    this.metrics = metrics;
  }

  @GetMapping("/control/v1/pricing")
  public PricingService.PricingEnvelope pricing() {
    // 完成组包后再计数，避免读库失败被记为成功拉取。
    PricingService.PricingEnvelope envelope = new PricingService.PricingEnvelope(pricing.list());
    metrics.count(
        MetricNames.CONTROL_PULL,
        MetricNames.TAG_PIPE,
        MetricNames.PIPE_PRICING,
        MetricNames.TAG_OUTCOME,
        MetricNames.OUTCOME_FULL);
    return envelope;
  }
}
