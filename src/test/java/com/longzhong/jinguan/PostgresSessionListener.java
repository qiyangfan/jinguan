package com.longzhong.jinguan;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/** 测试会话结束时停掉共享的 PostgreSQL 容器,避免 Ryuk 关闭时留下残留容器。 */
public class PostgresSessionListener implements LauncherSessionListener {

  @Override
  public void launcherSessionClosed(LauncherSession session) {
    PostgresContainerSupport.stop();
  }
}
