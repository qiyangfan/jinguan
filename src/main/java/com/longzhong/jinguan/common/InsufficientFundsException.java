package com.longzhong.jinguan.common;

/** 余额不足:扣账将致余额为负,账本不变量拒绝。 */
public class InsufficientFundsException extends DomainException {

  private static final long serialVersionUID = 1L;

  public InsufficientFundsException(String subject, long required, long balance) {
    super("余额不足: subject=%s 需要=%d 余额=%d".formatted(subject, required, balance));
  }
}
