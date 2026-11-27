package com.longzhong.jinguan.saga;

import com.longzhong.jinguan.entitlement.EntitlementService;
import com.longzhong.jinguan.tenant.TenantService;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 管理步骤执行/补偿:只写管理域数据,不代写服务配置。 */
@Component
class SagaAdminSteps {

  private static final String SYSTEM_ROLE = "SYSTEM";

  private final TenantService tenants;
  private final EntitlementService entitlements;

  SagaAdminSteps(TenantService tenants, EntitlementService entitlements) {
    this.tenants = tenants;
    this.entitlements = entitlements;
  }

  void execute(ControlSagaEntity saga, SagaStepEntity step, Map<String, Object> context) {
    String actor = "saga:" + saga.getId();
    String reason = "saga 自动执行: " + saga.getReason();
    switch (step.getName()) {
      case SagaStatus.STEP_CREATE_TENANT -> {
        var view =
            tenants.create(
                (String) context.get(SagaStatus.CONTEXT_TENANT_KEY),
                (String) context.get(SagaStatus.CONTEXT_NAME),
                reason,
                actor,
                SYSTEM_ROLE);
        context.put(SagaStatus.CONTEXT_TENANT_ID, view.id());
      }
      case SagaStatus.STEP_BIND_PLAN ->
          entitlements.bind(
              (String) context.get(SagaStatus.CONTEXT_TENANT_ID),
              (String) context.get(SagaStatus.CONTEXT_PLAN_KEY),
              reason,
              actor,
              SYSTEM_ROLE);
      default -> throw new IllegalStateException("未知管理步骤: " + step.getName());
    }
  }

  void compensate(ControlSagaEntity saga, SagaStepEntity step, Map<String, Object> context) {
    String actor = "saga:" + saga.getId();
    switch (step.getName()) {
      case SagaStatus.STEP_CREATE_TENANT ->
          tenants.changeEnabled(
              (String) context.get(SagaStatus.CONTEXT_TENANT_ID),
              false,
              "saga 补偿:停用自动开通的租户",
              actor,
              SYSTEM_ROLE);
      case SagaStatus.STEP_BIND_PLAN ->
          entitlements.unbind(
              (String) context.get(SagaStatus.CONTEXT_TENANT_ID),
              "saga 补偿:解除自动绑定",
              actor,
              SYSTEM_ROLE);
      default -> throw new IllegalStateException("未知管理步骤: " + step.getName());
    }
  }
}
