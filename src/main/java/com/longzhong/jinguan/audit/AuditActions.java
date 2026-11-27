package com.longzhong.jinguan.audit;

/** 控制面审计动作名:跨模块共用,便于查询与告警引用。 */
public final class AuditActions {

  public static final String TENANT_CREATE = "TENANT_CREATE";
  public static final String TENANT_UPDATE = "TENANT_UPDATE";
  public static final String TENANT_DISABLE = "TENANT_DISABLE";
  public static final String TENANT_ENABLE = "TENANT_ENABLE";
  public static final String TENANT_MEMBER_ADD = "TENANT_MEMBER_ADD";
  public static final String TENANT_MEMBER_UPDATE = "TENANT_MEMBER_UPDATE";
  public static final String TENANT_MEMBER_DISABLE = "TENANT_MEMBER_DISABLE";
  public static final String PLAN_CREATE = "PLAN_CREATE";
  public static final String PLAN_UPDATE = "PLAN_UPDATE";
  public static final String PLAN_DELETE = "PLAN_DELETE";
  public static final String TENANT_PLAN_BIND = "TENANT_PLAN_BIND";
  public static final String TENANT_PLAN_UNBIND = "TENANT_PLAN_UNBIND";
  public static final String POLICY_CREATE = "POLICY_CREATE";
  public static final String POLICY_ACTIVATE = "POLICY_ACTIVATE";
  public static final String POLICY_ROLLBACK = "POLICY_ROLLBACK";
  public static final String CATALOG_CREATE = "CATALOG_CREATE";
  public static final String CATALOG_UPDATE = "CATALOG_UPDATE";
  public static final String CATALOG_DISABLE = "CATALOG_DISABLE";
  public static final String SERVICE_REGISTER = "SERVICE_REGISTER";
  public static final String SERVICE_STALE_PURGE = "SERVICE_STALE_PURGE";
  public static final String SERVICE_AUTH_REJECT = "SERVICE_AUTH_REJECT";
  public static final String SERVICE_IDENTITY_VIEW = "SERVICE_IDENTITY_VIEW";
  public static final String COMMAND_SUBMIT = "COMMAND_SUBMIT";
  public static final String COMMAND_CLAIM = "COMMAND_CLAIM";
  public static final String COMMAND_ACK = "COMMAND_ACK";
  public static final String COMMAND_DEAD_LETTER = "COMMAND_DEAD_LETTER";
  public static final String COMMAND_PURGE = "COMMAND_PURGE";
  public static final String USAGE_RECEIVE = "USAGE_RECEIVE";
  public static final String USAGE_REJECT = "USAGE_REJECT";
  public static final String USAGE_AGGREGATE = "USAGE_AGGREGATE";
  public static final String USAGE_PURGE = "USAGE_PURGE";
  public static final String HEALTH_RECEIVE = "HEALTH_RECEIVE";
  public static final String PROJECTION_VIEW = "PROJECTION_VIEW";
  public static final String PROJECTION_EXPORT = "PROJECTION_EXPORT";
  public static final String PROJECTION_PURGE = "PROJECTION_PURGE";
  public static final String SAGA_START = "SAGA_START";
  public static final String SAGA_STEP = "SAGA_STEP";
  public static final String SAGA_COMPLETE = "SAGA_COMPLETE";
  public static final String SAGA_COMPENSATE = "SAGA_COMPENSATE";
  public static final String SAGA_BLOCKED = "SAGA_BLOCKED";
  public static final String SAGA_CLOSE = "SAGA_CLOSE";
  public static final String SAGA_PURGE = "SAGA_PURGE";
  public static final String AUDIT_EXPORT = "AUDIT_EXPORT";
  public static final String RETENTION_CLEANUP = "RETENTION_CLEANUP";
  public static final String ALERT_RAISE = "ALERT_RAISE";
  public static final String ALERT_ACK = "ALERT_ACK";
  public static final String ALERT_PURGE = "ALERT_PURGE";
  public static final String RATE_LIMITED = "RATE_LIMITED";

  private AuditActions() {}
}
