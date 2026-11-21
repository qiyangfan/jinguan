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

  @Column(name = "subject", nullable = false)
  private String subject;

  @Column(name = "amount_credits", nullable = false)
  private long amountCredits;

  @Column(nullable = false, length = 32)
  private String reason; // 取值见 WalletService.REASON_*

  @Column(name = "ref_id", nullable = false, unique = true, length = 128)
  private String refId;

  @Column(nullable = true)
  private String note;

  /** 管理员资金操作的操作人；支付和计量入账为空。 */
  @Column(name = "operator_subject")
  private String operatorSubject;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @JsonSerialize(using = ToStringSerializer.class)
  public Long getId() {
    return id;
  }

  public String getSubject() {
    return subject;
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

  public String getOperatorSubject() {
    return operatorSubject;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  static LedgerEntryEntity of(
      String subject,
      long amountCredits,
      String reason,
      String refId,
      String note,
      String operatorSubject) {
    LedgerEntryEntity e = new LedgerEntryEntity();
    e.subject = subject;
    e.amountCredits = amountCredits;
    e.reason = reason;
    e.refId = refId;
    e.note = note;
    e.operatorSubject = operatorSubject;
    return e;
  }
}
