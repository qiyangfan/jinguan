package com.longzhong.jinguan.common;

/** 账号已禁用:登录与续期一律拒绝,独立于欠费停服(suspended_at)。 */
public class AccountDisabledException extends DomainException {

  private static final long serialVersionUID = 1L;

  public AccountDisabledException() {
    super("账号已禁用");
  }
}
