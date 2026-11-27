package com.longzhong.jinguan.saga;

/** 跨域流程状态与步骤词汇;状态机不引入分布式事务。 */
public final class SagaStatus {

  public static final String PENDING = "PENDING";
  public static final String RUNNING = "RUNNING";
  public static final String SUCCEEDED = "SUCCEEDED";
  public static final String FAILED = "FAILED";
  public static final String COMPENSATING = "COMPENSATING";
  public static final String COMPENSATED = "COMPENSATED";
  public static final String BLOCKED = "BLOCKED";

  public static final String TENANT_ONBOARD = "tenant_onboard";

  public static final String STEP_ADMIN = "ADMIN";
  public static final String STEP_SERVICE = "SERVICE_COMMAND";

  public static final String STEP_CREATE_TENANT = "create_tenant";
  public static final String STEP_BIND_PLAN = "bind_plan";
  public static final String STEP_TENANT_PROVISION = "tenant_provision";
  public static final String STEP_PLAN_APPLY = "plan_apply";

  public static final String ACTION_TENANT_PROVISION = "tenant_provision";
  public static final String ACTION_TENANT_DEPROVISION = "tenant_deprovision";
  public static final String ACTION_PLAN_APPLY = "plan_apply";
  public static final String ACTION_PLAN_REVERT = "plan_revert";

  public static final String CONTEXT_TENANT_KEY = "tenantKey";
  public static final String CONTEXT_TENANT_ID = "tenantId";
  public static final String CONTEXT_NAME = "name";
  public static final String CONTEXT_PLAN_KEY = "planKey";
  public static final String CONTEXT_COMPENSATION_PREFIX = "comp:";
  public static final String PARAM_TENANT_KEY = "tenant_key";
  public static final String PARAM_PLAN_KEY = "plan_key";

  private SagaStatus() {}
}
