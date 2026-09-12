package com.longzhong.jinguan.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 账本分录(append-only):应用层只 INSERT,禁止 UPDATE/DELETE——审计与对账的根基。 amountMicros 有符号(+入账/-扣账),余额 =
 * SUM;refId 全局唯一,是全部写入路径的幂等键。
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "amount_micros", nullable = false)
  private long amountMicros;

  @Column(nullable = false, length = 32)
  private String reason; // MANUAL_TOPUP | PAYMENT_TOPUP | USAGE

  @Column(name = "ref_id", nullable = false, unique = true, length = 128)
  private String refId;

  @Column(nullable = true)
  private String note;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public long getAmountMicros() {
    return amountMicros;
  }

  public String getReason() {
    return reason;
  }

  public String getRefId() {
    return refId;
  }

  public String getNote() {
    return note;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  static LedgerEntryEntity of(
      long userId, long amountMicros, String reason, String refId, String note) {
    LedgerEntryEntity e = new LedgerEntryEntity();
    e.userId = userId;
    e.amountMicros = amountMicros;
    e.reason = reason;
    e.refId = refId;
    e.note = note;
    return e;
  }
}
