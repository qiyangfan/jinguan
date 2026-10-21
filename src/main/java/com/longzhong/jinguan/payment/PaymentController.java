package com.longzhong.jinguan.payment;

import com.longzhong.jinguan.security.JwtService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
      @NotBlank String provider, @Positive long amountCredits, @NotBlank String currency) {}

  /** 当前用户创建支付单。 */
  @PostMapping("/orders")
  public Map<String, Object> create(
      @AuthenticationPrincipal JwtService.LoginUser user,
      @RequestBody @Validated CreateRequest req) {
    return payments.createOrder(req.provider(), user.uid(), req.amountCredits(), req.currency());
  }

  /** 模拟收银台确认支付。 */
  @PostMapping("/mock/{orderId}/pay")
  public Map<String, Object> mockPay(@PathVariable String orderId) {
    return payments.handleCallback(
        MockPaymentProvider.NAME,
        orderId,
        new PaymentProvider.CallbackPayload(orderId, "mock-" + orderId, "PAID", "mock-signature"));
  }

  /** 分页查询当前用户的订单。 */
  @GetMapping("/orders")
  public List<MockOrderEntity> orders(
      @AuthenticationPrincipal JwtService.LoginUser user,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return payments.list(user.uid(), page, size).getContent();
  }

  /** 查询本人订单；非本人订单返回 404。 */
  @GetMapping("/orders/{orderId}")
  public Map<String, Object> query(
      @AuthenticationPrincipal JwtService.LoginUser user, @PathVariable String orderId) {
    return payments.query(MockPaymentProvider.NAME, orderId, user.uid());
  }
}
