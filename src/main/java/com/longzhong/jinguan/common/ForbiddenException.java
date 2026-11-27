package com.longzhong.jinguan.common;

/** 身份有效但无权限或已被吊销(HTTP 403)。 */
public class ForbiddenException extends DomainException {

  private static final long serialVersionUID = 1L;

  public ForbiddenException(String message) {
    super(message);
  }
}
