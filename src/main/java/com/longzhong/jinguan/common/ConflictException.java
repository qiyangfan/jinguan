package com.longzhong.jinguan.common;

/** 状态冲突:目标被引用或已存在不兼容状态(HTTP 409)。 */
public class ConflictException extends DomainException {

  private static final long serialVersionUID = 1L;

  public ConflictException(String message) {
    super(message);
  }
}
