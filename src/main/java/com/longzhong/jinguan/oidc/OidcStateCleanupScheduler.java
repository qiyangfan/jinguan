package com.longzhong.jinguan.oidc;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 周期清理过期登录 state：登录中请求本就不可用，及时删除避免表无限增长。 */
@Component
class OidcStateCleanupScheduler {

  private final OidcLoginStateRepository states;

  OidcStateCleanupScheduler(OidcLoginStateRepository states) {
    this.states = states;
  }

  @Scheduled(
      fixedDelayString = "${jinguan.oidc.state-cleanup-seconds:600}",
      initialDelayString = "${jinguan.oidc.state-cleanup-seconds:600}",
      timeUnit = java.util.concurrent.TimeUnit.SECONDS)
  @Transactional
  public void purgeExpiredStates() {
    states.deleteExpired(Instant.now());
  }
}
