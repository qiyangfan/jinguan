package com.longzhong.jinguan.wallet;

import com.longzhong.jinguan.security.JwtService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wallet")
@Validated
public class WalletController {

  private final WalletService wallet;

  public WalletController(WalletService wallet) {
    this.wallet = wallet;
  }

  public record CreditRequest(
      @NotNull Long userId, @Positive long amountMicros, String refId, String note) {}

  /** 当前用户余额(micros)。 */
  @GetMapping("/balance")
  public Map<String, Object> balance(@AuthenticationPrincipal JwtService.LoginUser user) {
    return Map.of("userId", user.uid(), "balanceMicros", wallet.balance(user.uid()));
  }

  /** 当前用户账本流水(新→旧)。 */
  @GetMapping("/entries")
  public List<LedgerEntryEntity> entries(
      @AuthenticationPrincipal JwtService.LoginUser user,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return wallet.history(user.uid(), page, size).getContent();
  }

  /** 管理员手工入账(兜底通道;与支付入账走同一账本,refId 幂等)。目标用户按 userId。 */
  @PostMapping("/admin/credit")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Map<String, Object>> credit(@RequestBody @Validated CreditRequest req) {
    String refId =
        req.refId() == null || req.refId().isBlank() ? "manual-" + UUID.randomUUID() : req.refId();
    LedgerEntryEntity entry =
        wallet
            .credit(
                req.userId(),
                req.amountMicros(),
                WalletService.REASON_MANUAL_TOPUP,
                refId,
                req.note())
            .entry();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("refId", entry.getRefId(), "balanceMicros", wallet.balance(req.userId())));
  }
}
