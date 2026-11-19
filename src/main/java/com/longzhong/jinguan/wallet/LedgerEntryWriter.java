package com.longzhong.jinguan.wallet;

import com.longzhong.jinguan.common.IdempotencyConflictException;
import org.springframework.stereotype.Component;

/** 账本幂等写入：按 refId 裁决新写、重放或载荷冲突。 */
@Component
class LedgerEntryWriter {

  private final LedgerEntryRepository ledger;

  LedgerEntryWriter(LedgerEntryRepository ledger) {
    this.ledger = ledger;
  }

  WalletService.EntryResult insertOrReplay(
      long userId, long amount, String reason, String refId, String note, Long operatorUserId) {
    var existing = ledger.findByRefId(refId);
    if (existing.isPresent()) {
      return adjudicate(existing.get(), userId, amount, reason);
    }
    return new WalletService.EntryResult(
        ledger.save(LedgerEntryEntity.of(userId, amount, reason, refId, note, operatorUserId)),
        false);
  }

  /** 同 refId 的用户、金额或原因不一致时抛出幂等冲突。 */
  WalletService.EntryResult adjudicate(
      LedgerEntryEntity existing, long userId, long amount, String reason) {
    if (existing.getUserId() != userId
        || existing.getAmountCredits() != amount
        || !existing.getReason().equals(reason)) {
      throw new IdempotencyConflictException(existing.getRefId());
    }
    return new WalletService.EntryResult(existing, true);
  }
}
