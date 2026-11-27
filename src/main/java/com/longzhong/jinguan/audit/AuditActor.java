package com.longzhong.jinguan.audit;

/** 审计主体:用户、服务身份或系统任务。 */
public record AuditActor(String subject, String role, Kind kind) {

  /** 审计主体类型。 */
  public enum Kind {
    USER,
    SERVICE,
    SYSTEM
  }

  public static AuditActor user(String subject, String role) {
    return new AuditActor(subject, role, Kind.USER);
  }

  public static AuditActor service(String serviceId) {
    return new AuditActor("service:" + serviceId, null, Kind.SERVICE);
  }

  public static AuditActor system() {
    return new AuditActor("system", null, Kind.SYSTEM);
  }
}
