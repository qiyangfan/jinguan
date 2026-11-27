package com.longzhong.jinguan.alerts;

/** 告警类型与严重度:类型是固定枚举,载荷不得含敏感值。 */
public final class AlertTypes {

  public static final String CONTROL_UNAVAILABLE = "CONTROL_UNAVAILABLE";
  public static final String DB_FAILURE = "DB_FAILURE";
  public static final String BACKUP_FAILURE = "BACKUP_FAILURE";
  public static final String SAGA_BLOCKED = "SAGA_BLOCKED";
  public static final String IDENTITY_REVOKED = "IDENTITY_REVOKED";
  public static final String CONTRACT_DRIFT = "CONTRACT_DRIFT";
  public static final String RATE_LIMITED = "RATE_LIMITED";
  public static final String AUTH_FAILURE = "AUTH_FAILURE";

  private AlertTypes() {}
}
