package com.longzhong.jinguan.audit;

import com.longzhong.jinguan.lease.LeaseService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 保留期归档清理;单实例租约执行,避免多实例重复清理。 */
@Component
public class AuditRetentionScheduler {

  private static final String LEASE = "audit-retention";

  private final AuditRetentionService retention;
  private final LeaseService leases;
  private final int retentionDays;

  public AuditRetentionScheduler(
      AuditRetentionService retention,
      LeaseService leases,
      @Value("${jinguan.audit.online-retention-days:180}") int retentionDays) {
    this.retention = retention;
    this.leases = leases;
    this.retentionDays = retentionDays;
  }

  @Scheduled(
      fixedDelayString = "${jinguan.audit.cleanup-interval-seconds:3600}000",
      initialDelayString = "${jinguan.audit.cleanup-initial-delay-seconds:60}000")
  public void cleanup() {
    if (!leases.tryAcquire(LEASE, Duration.ofMinutes(30))) {
      return;
    }
    try {
      retention.archiveAndPurge(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
    } finally {
      leases.release(LEASE);
    }
  }
}
