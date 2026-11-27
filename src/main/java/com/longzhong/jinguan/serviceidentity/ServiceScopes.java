package com.longzhong.jinguan.serviceidentity;

/** 服务接入的 scope 名:注册/快照/命令/用量;只读方向为 jinguan 出站使用。 */
public final class ServiceScopes {

  public static final String REGISTRY = "registry";
  public static final String SNAPSHOT = "snapshot";
  public static final String COMMAND = "command";
  public static final String USAGE = "usage";
  public static final String READ = "read";

  /** 锦官内部 scope 与符节/Keycloak client scope 的映射;managed 模式按此校验 token。 */
  static String keycloakScope(String scope) {
    return switch (scope) {
      case REGISTRY -> "control:register";
      case SNAPSHOT -> "control:snapshot";
      case COMMAND -> "control:command";
      case USAGE -> "control:usage";
      case READ -> "service:read";
      default -> scope;
    };
  }

  private ServiceScopes() {}
}
