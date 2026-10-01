package com.longzhong.jinguan.pricing;

import com.longzhong.jinguan.common.CommittedMetrics;
import com.longzhong.jinguan.common.MetricNames;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 下发契约端点(入站适配器):muniu 数据面按 pricing_url 轮询拉取(无认证 GET, 依赖部署网络隔离——见 SecurityConfig 注记),负载形态与 muniu
 * 期待逐字段一致。
 */
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
    // 拉取活性计数点在端点层:「有没有人在拉、拉到的是整包」只有这里知道,管理页复用 service 不计。
    // 计数必须后置到成包之后:读库故障也计 full 会让 control_pull 的「increase==0=下发链路断」
    // 判据在最需要触发的时刻永不成立(三管同口径,见 topology)
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
