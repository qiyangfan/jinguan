package com.longzhong.jinguan.wallet;

import com.longzhong.jinguan.payment.PaymentPaidEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 账务监听支付成功事件:BEFORE_COMMIT 同步执行——与支付订单状态变更同事务原子提交 (强一致:资金入账不接受"订单已支付但入账丢失"),幂等由账本
 * refId("pay:orderId") 唯一约束兜底。规模化演进项:切换 Modulith Event Publication Registry(持久化+重放)。
 */
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
        null); // 支付入账无管理员操作人(审计列仅管理员资金操作填写)
  }
}
