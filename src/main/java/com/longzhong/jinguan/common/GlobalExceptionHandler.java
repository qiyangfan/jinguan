package com.longzhong.jinguan.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将各类异常统一转换为 RFC 7807 ProblemDetail。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(OrderExpiredException.class)
  public ProblemDetail expired(OrderExpiredException e) {
    return problem(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(NotFoundException.class)
  public ProblemDetail notFound(NotFoundException e) {
    return problem(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ProblemDetail conflict(IdempotencyConflictException e) {
    return problem(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(KeyRevokedException.class)
  public ProblemDetail keyRevoked(KeyRevokedException e) {
    return problem(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(InsufficientFundsException.class)
  public ProblemDetail unprocessable(InsufficientFundsException e) {
    return problem(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  public ProblemDetail storage(org.springframework.dao.DataIntegrityViolationException e) {
    // 以非 2xx 告知数据面重试，详细原因只写入服务端日志。
    log.error("数据写入失败(DataIntegrityViolation)", e);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "数据写入失败");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail badRequest(IllegalArgumentException e) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail invalid(MethodArgumentNotValidException e) {
    String detail =
        e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("参数非法");
    return problem(HttpStatus.BAD_REQUEST, detail);
  }

  // 显式返回 400，避免 /error 分发被安全规则改写为 403。
  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  public ProblemDetail unreadable(
      org.springframework.http.converter.HttpMessageNotReadableException e) {
    return problem(HttpStatus.BAD_REQUEST, "请求体格式有误,请检查字段与类型后重试");
  }

  private ProblemDetail problem(HttpStatus status, String detail) {
    return ProblemDetail.forStatusAndDetail(status, detail);
  }
}
