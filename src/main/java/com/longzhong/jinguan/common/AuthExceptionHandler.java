package com.longzhong.jinguan.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 认证异常映射:refresh 重放 401、账号禁用 403。 */
@RestControllerAdvice
public class AuthExceptionHandler {

  @ExceptionHandler(RefreshReuseException.class)
  public ProblemDetail refreshReuse(RefreshReuseException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
  }

  @ExceptionHandler(AccountDisabledException.class)
  public ProblemDetail disabled(AccountDisabledException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
  }
}
