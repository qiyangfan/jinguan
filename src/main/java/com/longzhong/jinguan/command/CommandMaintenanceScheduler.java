package com.longzhong.jinguan.command;

import com.longzhong.jinguan.lease.LeaseService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 命令超时扫描与终态清理;均以租约单实例执行。 */
@Component
public class CommandMaintenanceScheduler {

  private static final String TIMEOUT_LEASE = "command-timeout-sweep";
  private static final String RETENTION_LEASE = "command-retention";

  private final CommandMaintenanceService maintenance;
  private final LeaseService leases;
  private final int retentionDays;

  public CommandMaintenanceScheduler(
      CommandMaintenanceService maintenance,
      LeaseService leases,
      @Value("${jinguan.commands.retention-days:30}") int retentionDays) {
    this.maintenance = maintenance;
    this.leases = leases;
    this.retentionDays = retentionDays;
  }

  @Scheduled(
      fixedDelayString = "${jinguan.commands.sweep-interval-seconds:30}000",
      initialDelayString = "${jinguan.commands.sweep-initial-delay-seconds:30}000")
  public void sweepTimeouts() {
    if (!leases.tryAcquire(TIMEOUT_LEASE, Duration.ofMinutes(5))) {
      return;
    }
    try {
      Map<String, Integer> result = maintenance.sweepTimeouts();
      if (result.get("retried") > 0 || result.get("deadLettered") > 0) {
        org.slf4j.LoggerFactory.getLogger(CommandMaintenanceScheduler.class)
            .info(
                "命令超时扫描 retried={} deadLettered={}",
                result.get("retried"),
                result.get("deadLettered"));
      }
    } finally {
      leases.release(TIMEOUT_LEASE);
    }
  }

  @Scheduled(
      fixedDelayString = "${jinguan.commands.cleanup-interval-seconds:3600}000",
      initialDelayString = "${jinguan.commands.cleanup-initial-delay-seconds:300}000")
  public void purgeFinished() {
    if (!leases.tryAcquire(RETENTION_LEASE, Duration.ofMinutes(30))) {
      return;
    }
    try {
      maintenance.purgeFinished(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
    } finally {
      leases.release(RETENTION_LEASE);
    }
  }
}
