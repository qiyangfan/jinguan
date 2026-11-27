package com.longzhong.jinguan.registry;

import com.longzhong.jinguan.lease.LeaseService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** stale 记录保留期清理;活跃实例不删,清理留审计。 */
@Component
public class ServiceRegistryCleanupScheduler {

  private static final String LEASE = "registry-stale-purge";

  private final ServiceRegistryService registry;
  private final LeaseService leases;
  private final int retentionDays;

  public ServiceRegistryCleanupScheduler(
      ServiceRegistryService registry,
      LeaseService leases,
      @Value("${jinguan.registry.retention-days:7}") int retentionDays) {
    this.registry = registry;
    this.leases = leases;
    this.retentionDays = retentionDays;
  }

  @Scheduled(
      fixedDelayString = "${jinguan.registry.cleanup-interval-seconds:3600}000",
      initialDelayString = "${jinguan.registry.cleanup-initial-delay-seconds:120}000")
  public void cleanup() {
    if (!leases.tryAcquire(LEASE, Duration.ofMinutes(30))) {
      return;
    }
    try {
      registry.purgeStale(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
    } finally {
      leases.release(LEASE);
    }
  }
}
