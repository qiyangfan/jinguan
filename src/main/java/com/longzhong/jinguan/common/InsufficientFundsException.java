package com.longzhong.jinguan.common;

/** 余额不足:扣账将致余额为负,账本不变量拒绝。 */
public class InsufficientFundsException extends DomainException {

  public InsufficientFundsException(long userId, long required, long balance) {
    super("余额不足: user=%d 需要=%d 余额=%d".formatted(userId, required, balance));
  }
}
