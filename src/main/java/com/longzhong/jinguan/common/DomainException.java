package com.longzhong.jinguan.common;

/** 领域异常基类:业务语义错误(映射 HTTP 状态由 handler 统一裁决)。 */
public class DomainException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  protected DomainException(String message) {
    super(message);
  }
}
