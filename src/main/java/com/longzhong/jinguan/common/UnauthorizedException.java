package com.longzhong.jinguan.common;

/** 缺少或无法校验的调用方身份(HTTP 401)。 */
public class UnauthorizedException extends DomainException {

  private static final long serialVersionUID = 1L;

  public UnauthorizedException(String message) {
    super(message);
  }
}
