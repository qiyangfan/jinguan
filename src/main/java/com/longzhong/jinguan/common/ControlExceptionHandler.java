package com.longzhong.jinguan.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 控制面通用错误映射:认证/授权/冲突/上游超时。 */
@RestControllerAdvice
public class ControlExceptionHandler {

  @ExceptionHandler(ConflictException.class)
  public ProblemDetail conflict(ConflictException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ProblemDetail unauthorized(UnauthorizedException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
  }

  @ExceptionHandler(ForbiddenException.class)
  public ProblemDetail forbidden(ForbiddenException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
  }

  @ExceptionHandler(UpstreamTimeoutException.class)
  public ProblemDetail upstreamTimeout(UpstreamTimeoutException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, e.getMessage());
  }
}
