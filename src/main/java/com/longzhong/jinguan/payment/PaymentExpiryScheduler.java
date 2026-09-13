package com.longzhong.jinguan.payment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 过期收敛扫描器:游标批量(keyset)推进,单轮至多 batch 笔,满批即继续—— 积压场景下以固定速率消化而非单轮锁死。职责边界——只做【展示与状态及时性】
 * 收敛,资金正确性由回调守门(懒过期比对 expires_at)保证,扫描延迟无资金影响。
 *
 * <p>方案谱系与取舍(为什么不是 MQ 延迟消息/时间轮/xxl-job)及演进触发条件见 docs/design/payment-timeout.md。多实例部署加
 * ShedLock(单实例语义见 README)。
 *
 * <p>可观测:converged 累计收敛笔数 / scan 扫描耗时直方图 / backlog 已过期未收敛存量。
 */
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
        Counter.builder("jinguan.payment.expiry.converged")
            .description("累计过期收敛笔数(CREATED→EXPIRED)")
            .register(registry);
    this.scanTimer =
        Timer.builder("jinguan.payment.expiry.scan").description("过期收敛扫描耗时").register(registry);
  }

  /** 已过期未收敛存量(最近一轮,可观测暴露用)。 */
  public long backlog() {
    return backlog;
  }

  /**
   * 生产入口:按配置批量上限推进。刻意不加方法级事务:expireIfCreated 为原子条件 UPDATE(自带 CAS
   * 语义,每轮独立短事务提交),避免积压场景多轮循环在同一事务内长时间持有过期订单行锁、阻塞并发回调。
   */
  @Scheduled(
      fixedDelayString = "${jinguan.payment.expiry-scan-seconds:30}",
      initialDelayString = "${jinguan.payment.expiry-scan-seconds:30}",
      timeUnit = java.util.concurrent.TimeUnit.SECONDS)
  public void expireOverdueOrders() {
    expireOverdueOrders(batchSize);
  }

  /**
   * 执行体(批量为参数,测试/未来源复用):满批循环推进至收敛完成。 ⚠ 事务边界:@Transactional 仅经 @Scheduled 代理调用生效(同类自调用不经代理);
   * 测试直接调用时由测试事务提供上下文。
   */
  public void expireOverdueOrders(int batchLimit) {
    scanTimer.record(
        () -> {
          Instant now = Instant.now();
          int total = 0, rounds = 0, batch;
          do { // 满批继续推进:积压按 batch 步进消化;空批/不满批即收敛完成
            var candidates = orders.findOverdue("CREATED", now, PageRequest.of(0, batchLimit));
            batch = candidates.size();
            if (batch == 0) {
              break;
            }
            // 条件批量迁移(与回调 CAS 对称):updated 可能 < fetched(并发已被支付),收敛计数以实际迁移为准
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
