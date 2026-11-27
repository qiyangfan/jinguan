package com.longzhong.jinguan.alerts;

import com.longzhong.jinguan.lease.LeaseService;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 告警投递任务:租约单实例,webhook 优先、邮件兜底;失败重试到上限。 */
@Component
public class AlertDeliveryScheduler {

  private static final String LEASE = "alert-delivery";
  private static final Logger log = LoggerFactory.getLogger(AlertDeliveryScheduler.class);

  private final AlertEventRepository repository;
  private final WebhookAlertChannel webhook;
  private final EmailAlertChannel email;
  private final AlertProperties properties;
  private final LeaseService leases;
  private final TransactionTemplate transaction;

  public AlertDeliveryScheduler(
      AlertEventRepository repository,
      WebhookAlertChannel webhook,
      EmailAlertChannel email,
      AlertProperties properties,
      LeaseService leases,
      PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.webhook = webhook;
    this.email = email;
    this.properties = properties;
    this.leases = leases;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  @Scheduled(
      fixedDelayString = "${jinguan.alerts.delivery-interval-seconds:30}000",
      initialDelayString = "${jinguan.alerts.delivery-initial-delay-seconds:15}000")
  public void deliverPending() {
    if (!leases.tryAcquire(LEASE, Duration.ofMinutes(10))) {
      return;
    }
    try {
      List<AlertEventEntity> pending =
          repository.findByStatusOrderByCreatedAtAsc("PENDING", Limit.of(50));
      for (AlertEventEntity event : pending) {
        deliverOne(event.getId());
      }
    } finally {
      leases.release(LEASE);
    }
  }

  /** 单条投递:网络调用在事务外,状态更新各自独立提交。 */
  public void deliverOne(String id) {
    var event = repository.findById(id);
    if (event.isEmpty() || !"PENDING".equals(event.get().getStatus())) {
      return;
    }
    AlertEventEntity current = event.get();
    if (!webhook.enabled() && !email.enabled()) {
      transaction.executeWithoutResult(
          status -> {
            current.failImmediately("未配置告警投递通道(webhook/email)");
            repository.save(current);
          });
      return;
    }
    try {
      if (webhook.enabled()) {
        webhook.deliver(current);
      }
      if (email.enabled()) {
        email.deliver(current);
      }
      transaction.executeWithoutResult(
          status -> {
            current.markDelivered();
            repository.save(current);
          });
    } catch (Exception e) {
      log.warn("告警投递失败 id={} type={}: {}", current.getId(), current.getType(), e.getMessage());
      String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      transaction.executeWithoutResult(
          status -> {
            current.markAttemptFailed(message, properties.maxAttempts());
            repository.save(current);
          });
    }
  }
}
