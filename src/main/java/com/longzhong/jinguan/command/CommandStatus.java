package com.longzhong.jinguan.command;

import java.util.List;

/** 命令状态机常量;终态命令才允许保留期清理。 */
public final class CommandStatus {

  public static final String PENDING = "PENDING";
  public static final String CLAIMED = "CLAIMED";
  public static final String SUCCEEDED = "SUCCEEDED";
  public static final String FAILED = "FAILED";
  public static final String DEAD_LETTER = "DEAD_LETTER";
  public static final List<String> TERMINAL = List.of(SUCCEEDED, FAILED, DEAD_LETTER);

  private CommandStatus() {}
}
