package com.longzhong.jinguan.common;

/** 资源不存在。 */
public class NotFoundException extends DomainException {

  private static final long serialVersionUID = 1L;

  public NotFoundException(String message) {
    super(message);
  }
}
