package com.longzhong.jinguan.projection;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import com.longzhong.jinguan.lease.LeaseService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 投影缓存保留期清理;单实例租约执行,清理留审计。 */
@Component
public class ProjectionCleanupScheduler {

  private static final String LEASE = "projection-cleanup";

  private final ProjectionService projections;
  private final LeaseService leases;
  private final AuditRecorder recorder;
  private final int retentionDays;

  public ProjectionCleanupScheduler(
      ProjectionService projections,
      LeaseService leases,
      AuditRecorder recorder,
      ServiceReadProperties properties) {
    this.projections = projections;
    this.leases = leases;
    this.recorder = recorder;
    this.retentionDays = properties.retentionDays();
  }

  @Scheduled(
      fixedDelayString = "${jinguan.services.read.cleanup-interval-seconds:3600}000",
      initialDelayString = "${jinguan.services.read.cleanup-initial-delay-seconds:180}000")
  public void cleanup() {
    if (!leases.tryAcquire(LEASE, ProjectionService.cleanupLease())) {
      return;
    }
    try {
      int purged = projections.purgeExpired(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
      if (purged > 0) {
        recorder.record(
            AuditActor.system(),
            AuditActions.PROJECTION_PURGE,
            "service-projection",
            null,
            null,
            "投影保留期清理",
            Map.of("purged", purged),
            "SUCCESS");
      }
    } finally {
      leases.release(LEASE);
    }
  }
}
