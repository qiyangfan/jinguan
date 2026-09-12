package com.longzhong.jinguan.payment;

import com.longzhong.jinguan.security.JwtService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@Validated
public class PaymentController {

  private final PaymentService payments;

  public PaymentController(PaymentService payments) {
    this.payments = payments;
  }

  public record CreateRequest(
      @NotBlank String provider, @Positive long amountMicros, @NotBlank String currency) {}

  /** 当前用户创建支付单(演示闭环:mock 渠道返回本服务模拟收银台地址)。 */
  @PostMapping("/orders")
  public Map<String, Object> create(
      @AuthenticationPrincipal JwtService.LoginUser user,
      @RequestBody @Validated CreateRequest req) {
    return payments.createOrder(req.provider(), user.uid(), req.amountMicros(), req.currency());
  }

  /** 模拟收银台:mock 渠道的"用户完成支付"动作(真实渠道此步在渠道侧发生,经 webhook 通知)。 */
  @PostMapping("/mock/{orderId}/pay")
  public Map<String, Object> mockPay(@PathVariable String orderId) {
    return payments.handleCallback(
        MockPaymentProvider.NAME,
        orderId,
        new PaymentProvider.CallbackPayload(orderId, "mock-" + orderId, "PAID", "mock-signature"));
  }

  @GetMapping("/orders/{orderId}")
  public Map<String, Object> query(@PathVariable String orderId) {
    return payments.query(MockPaymentProvider.NAME, orderId);
  }
}
