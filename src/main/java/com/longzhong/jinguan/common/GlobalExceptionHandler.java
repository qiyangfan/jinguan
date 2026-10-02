package com.longzhong.jinguan.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 统一错误出口:RFC 7807 ProblemDetail;领域语义 → HTTP 状态的映射只在这一处。 */
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
    // 明细/约束类写入失败(如用量明细可靠保存失败):确定性 500,不泄露内部细节——
    // 可靠接收语义要求该类失败以非 2xx 暴露给数据面续传;成因完整留痕服务端日志(不吞异常)
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

  private ProblemDetail problem(HttpStatus status, String detail) {
    return ProblemDetail.forStatusAndDetail(status, detail);
  }
}
