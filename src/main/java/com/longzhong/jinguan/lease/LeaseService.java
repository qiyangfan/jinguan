package com.longzhong.jinguan.lease;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 调度器租约:每个进程持有唯一 owner,任务执行前先获取租约,过期可被其他实例抢占。 */
@Service
public class LeaseService {

  private final SchedulerLeaseRepository leases;
  private final TransactionTemplate requiresNew;
  private final String owner;

  public LeaseService(
      SchedulerLeaseRepository leases, PlatformTransactionManager transactionManager) {
    this.leases = leases;
    this.requiresNew = new TransactionTemplate(transactionManager);
    this.requiresNew.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    this.owner = hostOwner();
  }

  /** 尝试获取或续租;返回是否持有。 */
  public boolean tryAcquire(String name, Duration ttl) {
    Boolean acquired =
        requiresNew.execute(
            status -> {
              Instant now = Instant.now();
              Instant until = now.plus(ttl);
              var existing = leases.findWithLockByName(name);
              if (existing.isEmpty()) {
                leases.save(SchedulerLeaseEntity.held(name, owner, until));
                return true;
              }
              var lease = existing.get();
              if (!owner.equals(lease.getOwner()) && lease.getExpiresAt().isAfter(now)) {
                return false;
              }
              lease.takeOver(owner, until);
              leases.save(lease);
              return true;
            });
    return Boolean.TRUE.equals(acquired);
  }

  /** 释放租约(仅当仍由本进程持有)。 */
  public void release(String name) {
    requiresNew.executeWithoutResult(
        status ->
            leases
                .findWithLockByName(name)
                .filter(lease -> owner.equals(lease.getOwner()))
                .ifPresent(lease -> lease.takeOver("released", Instant.EPOCH)));
  }

  public String ownerId() {
    return owner;
  }

  private static String hostOwner() {
    String host;
    try {
      host = java.net.InetAddress.getLocalHost().getHostName();
    } catch (java.net.UnknownHostException e) {
      host = "unknown";
    }
    String pid = ManagementFactory.getRuntimeMXBean().getName();
    return host + ":" + pid + ":" + UUID.randomUUID().toString().substring(0, 8);
  }
}
