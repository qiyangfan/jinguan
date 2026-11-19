package com.longzhong.jinguan;

import java.util.concurrent.locks.ReentrantLock;
import org.testcontainers.containers.PostgreSQLContainer;

/** 整个测试 JVM 共享一个 PostgreSQL 容器,避免逐类启动触发 Podman API 连接失效。 */
final class PostgresContainerSupport {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine");
  private static final ReentrantLock LOCK = new ReentrantLock();
  private static boolean started;

  private PostgresContainerSupport() {}

  static PostgreSQLContainer<?> container() {
    LOCK.lock();
    try {
      if (!started) {
        POSTGRES.start();
        started = true;
      }
      return POSTGRES;
    } finally {
      LOCK.unlock();
    }
  }

  static String jdbcUrl() {
    return container().getJdbcUrl();
  }

  static String username() {
    return container().getUsername();
  }

  static String password() {
    return container().getPassword();
  }

  static void stop() {
    LOCK.lock();
    try {
      if (started) {
        POSTGRES.stop();
        started = false;
      }
    } finally {
      LOCK.unlock();
    }
  }
}
