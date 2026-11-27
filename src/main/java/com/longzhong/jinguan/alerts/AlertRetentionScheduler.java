package com.longzhong.jinguan.alerts;

import com.longzhong.jinguan.lease.LeaseService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 告警记录保留清理;租约单实例执行。 */
@Component
public class AlertRetentionScheduler {

  private static final String LEASE = "alert-retention";

  private final AlertRetentionService retention;
  private final LeaseService leases;
  private final int retentionDays;

  public AlertRetentionScheduler(
      AlertRetentionService retention,
      LeaseService leases,
      @Value("${jinguan.alerts.retention-days:90}") int retentionDays) {
    this.retention = retention;
    this.leases = leases;
    this.retentionDays = retentionDays;
  }

  @Scheduled(
      fixedDelayString = "${jinguan.alerts.retention-interval-seconds:86400}000",
      initialDelayString = "${jinguan.alerts.retention-initial-delay-seconds:600}000")
  public void cleanup() {
    if (!leases.tryAcquire(LEASE, Duration.ofMinutes(30))) {
      return;
    }
    try {
      retention.purgeTerminal(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
    } finally {
      leases.release(LEASE);
    }
  }
}
