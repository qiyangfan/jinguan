package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.lease.LeaseService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 用量聚合与原始事件保留清理;租约单实例执行。 */
@Component
public class UsageAggregationScheduler {

  private static final String AGGREGATE_LEASE = "usage-aggregation";
  private static final String RETENTION_LEASE = "usage-raw-retention";

  private final UsageEventService usageEvents;
  private final LeaseService leases;
  private final int retentionDays;

  public UsageAggregationScheduler(
      UsageEventService usageEvents,
      LeaseService leases,
      @Value("${jinguan.usage.raw-retention-days:90}") int retentionDays) {
    this.usageEvents = usageEvents;
    this.leases = leases;
    this.retentionDays = retentionDays;
  }

  @Scheduled(
      fixedDelayString = "${jinguan.usage.aggregate-interval-seconds:60}000",
      initialDelayString = "${jinguan.usage.aggregate-initial-delay-seconds:60}000")
  public void aggregate() {
    if (!leases.tryAcquire(AGGREGATE_LEASE, Duration.ofMinutes(5))) {
      return;
    }
    try {
      usageEvents.aggregatePending();
    } finally {
      leases.release(AGGREGATE_LEASE);
    }
  }

  @Scheduled(
      fixedDelayString = "${jinguan.usage.cleanup-interval-seconds:3600}000",
      initialDelayString = "${jinguan.usage.cleanup-initial-delay-seconds:300}000")
  public void purgeRaw() {
    if (!leases.tryAcquire(RETENTION_LEASE, Duration.ofMinutes(30))) {
      return;
    }
    try {
      usageEvents.purgeAggregated(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
    } finally {
      leases.release(RETENTION_LEASE);
    }
  }
}
