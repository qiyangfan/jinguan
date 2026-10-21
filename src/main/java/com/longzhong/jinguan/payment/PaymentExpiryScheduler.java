package com.longzhong.jinguan.payment;

import com.longzhong.jinguan.common.MetricNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 分批收敛已过期订单；支付回调仍负责校验有效期，扫描延迟不影响资金正确性。 */
@Component
public class PaymentExpiryScheduler {

  private static final Logger log = LoggerFactory.getLogger(PaymentExpiryScheduler.class);

  private final MockOrderRepository orders;
  private final int batchSize;
  private final Counter convergedCounter;
  private final Timer scanTimer;
  private volatile long backlog;

  public PaymentExpiryScheduler(
      MockOrderRepository orders,
      MeterRegistry registry,
      @Value("${jinguan.payment.expiry-batch:500}") int batchSize) {
    this.orders = orders;
    this.batchSize = Math.max(batchSize, 1);
    this.convergedCounter =
        Counter.builder(MetricNames.PAYMENT_EXPIRY_CONVERGED)
            .description("累计过期收敛笔数(CREATED→EXPIRED)")
            .register(registry);
    this.scanTimer =
        Timer.builder(MetricNames.PAYMENT_EXPIRY_SCAN).description("过期收敛扫描耗时").register(registry);
    Gauge.builder(MetricNames.PAYMENT_EXPIRY_BACKLOG, this, PaymentExpiryScheduler::backlog)
        .description("最近一轮已过期未收敛存量")
        .register(registry);
  }

  /** 最近一轮扫描后的待收敛数量。 */
  public long backlog() {
    return backlog;
  }

  /** 按配置批次定时收敛过期订单。 */
  @Scheduled(
      fixedDelayString = "${jinguan.payment.expiry-scan-seconds:30}",
      initialDelayString = "${jinguan.payment.expiry-scan-seconds:30}",
      timeUnit = java.util.concurrent.TimeUnit.SECONDS)
  public void expireOverdueOrders() {
    expireOverdueOrders(batchSize);
  }

  /** 分批推进至当前积压收敛完成或剩余批次不足。 */
  public void expireOverdueOrders(int batchLimit) {
    scanTimer.record(
        () -> {
          Instant now = Instant.now();
          int total = 0, rounds = 0, batch;
          do {
            var candidates = orders.findOverdue("CREATED", now, PageRequest.of(0, batchLimit));
            batch = candidates.size();
            if (batch == 0) {
              break;
            }
            // 并发回调可能已支付部分订单，只计实际迁移数。
            int updated =
                orders.expireIfCreated(
                    candidates.stream().map(MockOrderEntity::getOrderId).toList());
            total += updated;
            rounds++;
          } while (batch == batchLimit);
          backlog = orders.countByStatusAndExpiresAtBefore("CREATED", now);
          if (total > 0) {
            convergedCounter.increment(total);
            log.info("支付订单过期收敛: {} 笔 CREATED → EXPIRED(共 {} 轮),剩余积压 {}", total, rounds, backlog);
          }
        });
  }
}
