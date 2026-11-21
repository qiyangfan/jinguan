package com.longzhong.jinguan.user;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 周期删除已过期 refresh 会话行：过期票不可用，保留只会让表无限增长。 */
@Component
class RefreshSessionCleanupScheduler {

  private final RefreshSessionRepository sessions;

  RefreshSessionCleanupScheduler(RefreshSessionRepository sessions) {
    this.sessions = sessions;
  }

  @Scheduled(
      fixedDelayString = "${jinguan.security.refresh-cleanup-seconds:3600}",
      initialDelayString = "${jinguan.security.refresh-cleanup-seconds:3600}",
      timeUnit = java.util.concurrent.TimeUnit.SECONDS)
  @Transactional
  public void purgeExpiredSessions() {
    sessions.deleteExpired(Instant.now());
  }
}
