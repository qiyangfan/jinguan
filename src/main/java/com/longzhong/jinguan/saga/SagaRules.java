package com.longzhong.jinguan.saga;

import com.longzhong.jinguan.command.CommandStatus;
import java.time.Instant;
import java.util.Map;

/** 流程状态判定与命令映射(纯函数,便于单测与复用)。 */
final class SagaRules {

  private SagaRules() {}

  static boolean isTerminal(String status) {
    return SagaStatus.SUCCEEDED.equals(status)
        || SagaStatus.COMPENSATED.equals(status)
        || SagaStatus.BLOCKED.equals(status);
  }

  static boolean isStepDone(String status) {
    return SagaStatus.SUCCEEDED.equals(status) || SagaStatus.COMPENSATED.equals(status);
  }

  static boolean isCommandFailed(String status) {
    return CommandStatus.FAILED.equals(status) || CommandStatus.DEAD_LETTER.equals(status);
  }

  static boolean timedOut(SagaStepEntity step, int timeoutSeconds) {
    return step.getStartedAt() != null
        && step.getStartedAt().plusSeconds(timeoutSeconds).isBefore(Instant.now());
  }

  static String serviceIdOf(String action) {
    return SagaStatus.ACTION_TENANT_PROVISION.equals(action)
            || SagaStatus.ACTION_TENANT_DEPROVISION.equals(action)
        ? "kongming"
        : "muniu";
  }

  static String compensationActionOf(String action) {
    if (SagaStatus.ACTION_TENANT_PROVISION.equals(action)) {
      return SagaStatus.ACTION_TENANT_DEPROVISION;
    }
    if (SagaStatus.ACTION_PLAN_APPLY.equals(action)) {
      return SagaStatus.ACTION_PLAN_REVERT;
    }
    return null;
  }

  static tools.jackson.databind.JsonNode compensationParams(
      SagaContextCodec codec, Map<String, Object> context) {
    var node = codec.objectNode();
    node.put(SagaStatus.PARAM_TENANT_KEY, (String) context.get(SagaStatus.CONTEXT_TENANT_KEY));
    return node;
  }
}
