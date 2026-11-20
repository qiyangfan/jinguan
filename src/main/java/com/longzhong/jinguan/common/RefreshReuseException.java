package com.longzhong.jinguan.common;

/** 已消费 refresh 被重放(视为泄漏信号):吊销该用户全部 refresh 会话。 */
public class RefreshReuseException extends DomainException {

  private static final long serialVersionUID = 1L;

  public RefreshReuseException() {
    super("refresh 凭证已失效,请重新登录");
  }
}
