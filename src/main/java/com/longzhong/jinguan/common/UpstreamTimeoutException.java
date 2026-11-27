package com.longzhong.jinguan.common;

/** 上游服务调用超时(HTTP 504),用于只读投影等出站调用。 */
public class UpstreamTimeoutException extends DomainException {

  private static final long serialVersionUID = 1L;

  public UpstreamTimeoutException(String message) {
    super(message);
  }
}
