package com.longzhong.jinguan.wallet;

import com.longzhong.jinguan.payment.PaymentPaidEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 在支付订单事务提交前同步入账，确保订单状态与账本原子提交；refId 唯一约束保证幂等。 */
@Component
public class WalletEventListener {

  private final WalletService wallet;

  public WalletEventListener(WalletService wallet) {
    this.wallet = wallet;
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onPaymentPaid(PaymentPaidEvent event) {
    wallet.credit(
        event.userId(),
        event.amountCredits(),
        WalletService.REASON_PAYMENT_TOPUP,
        event.ledgerRefId(),
        "支付入账 " + event.currency(),
        null); // 支付入账不记录管理员操作人。
  }
}
