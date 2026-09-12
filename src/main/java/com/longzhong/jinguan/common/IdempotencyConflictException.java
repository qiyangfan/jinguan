package com.longzhong.jinguan.common;

/** 幂等冲突:同一 ref_id 的重复事件且载荷不一致(重放同载荷静默返回原结果,不抛此异常)。 */
public class IdempotencyConflictException extends DomainException {

  public IdempotencyConflictException(String refId) {
    super("幂等键冲突: " + refId);
  }
}
