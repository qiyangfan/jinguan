package com.longzhong.jinguan.saga;

import com.longzhong.jinguan.lease.LeaseService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 流程推进与保留清理;单实例租约,单条失败不影响其他流程。 */
@Component
public class SagaScheduler {

  private static final String ADVANCE_LEASE = "saga-advance";
  private static final String RETENTION_LEASE = "saga-retention";
  private static final Logger log = LoggerFactory.getLogger(SagaScheduler.class);

  private final SagaService sagas;
  private final SagaQueryService queries;
  private final SagaLifecycleService lifecycle;
  private final LeaseService leases;
  private final int retentionDays;

  public SagaScheduler(
      SagaService sagas,
      SagaQueryService queries,
      SagaLifecycleService lifecycle,
      LeaseService leases,
      @Value("${jinguan.sagas.retention-days:90}") int retentionDays) {
    this.sagas = sagas;
    this.queries = queries;
    this.lifecycle = lifecycle;
    this.leases = leases;
    this.retentionDays = retentionDays;
  }

  @Scheduled(
      fixedDelayString = "${jinguan.sagas.advance-interval-seconds:30}000",
      initialDelayString = "${jinguan.sagas.advance-initial-delay-seconds:30}000")
  public void advancePending() {
    if (!leases.tryAcquire(ADVANCE_LEASE, Duration.ofMinutes(5))) {
      return;
    }
    try {
      for (String id : queries.pendingIds()) {
        try {
          sagas.advance(id);
        } catch (RuntimeException e) {
          log.warn("saga 推进失败 id={}: {}", id, e.getMessage());
        }
      }
    } finally {
      leases.release(ADVANCE_LEASE);
    }
  }

  @Scheduled(
      fixedDelayString = "${jinguan.sagas.cleanup-interval-seconds:3600}000",
      initialDelayString = "${jinguan.sagas.cleanup-initial-delay-seconds:600}000")
  public void purge() {
    if (!leases.tryAcquire(RETENTION_LEASE, Duration.ofMinutes(30))) {
      return;
    }
    try {
      lifecycle.purge(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
    } finally {
      leases.release(RETENTION_LEASE);
    }
  }
}
