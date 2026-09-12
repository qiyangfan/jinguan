package com.longzhong.jinguan.common;

/** 资源不存在。 */
public class NotFoundException extends DomainException {

  public NotFoundException(String message) {
    super(message);
  }
}
