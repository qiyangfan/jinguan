package com.longzhong.jinguan.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/** 追加式账本分录；金额正数表示入账、负数表示扣账，余额为分录总和，refId 是全局幂等键。 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "amount_credits", nullable = false)
  private long amountCredits;

  @Column(nullable = false, length = 32)
  private String reason; // 取值见 WalletService.REASON_*

  @Column(name = "ref_id", nullable = false, unique = true, length = 128)
  private String refId;

  @Column(nullable = true)
  private String note;

  /** 管理员资金操作的操作人；支付和计量入账为空。 */
  @Column(name = "operator_user_id")
  private Long operatorUserId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @JsonSerialize(using = ToStringSerializer.class)
  public Long getId() {
    return id;
  }

  @JsonSerialize(using = ToStringSerializer.class)
  public Long getUserId() {
    return userId;
  }

  @JsonSerialize(using = ToStringSerializer.class)
  public long getAmountCredits() {
    return amountCredits;
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

  @JsonSerialize(using = ToStringSerializer.class)
  public Long getOperatorUserId() {
    return operatorUserId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  static LedgerEntryEntity of(
      long userId,
      long amountCredits,
      String reason,
      String refId,
      String note,
      Long operatorUserId) {
    LedgerEntryEntity e = new LedgerEntryEntity();
    e.userId = userId;
    e.amountCredits = amountCredits;
    e.reason = reason;
    e.refId = refId;
    e.note = note;
    e.operatorUserId = operatorUserId;
    return e;
  }
}
