package com.longzhong.jinguan.pricing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 下发契约端点(入站适配器):muniu 数据面按 pricing_url 轮询拉取(无认证 GET, 依赖部署网络隔离——见 SecurityConfig 注记),负载形态与 muniu
 * 期待逐字段一致。
 */
@RestController
public class PricingIngressController {

  private final PricingService pricing;

  public PricingIngressController(PricingService pricing) {
    this.pricing = pricing;
  }

  @GetMapping("/control/v1/pricing")
  public PricingService.PricingEnvelope pricing() {
    return new PricingService.PricingEnvelope(pricing.list());
  }
}
