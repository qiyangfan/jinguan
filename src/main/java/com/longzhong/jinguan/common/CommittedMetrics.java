package com.longzhong.jinguan.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 集中注册指标并按事务结果计数；成功分录提交后计数，拒绝结果立即计数。 */
@Component
public class CommittedMetrics {

  private static final List<String> WALLET_KINDS =
      List.of(MetricNames.KIND_CREDIT, MetricNames.KIND_DEBIT);

  private final MeterRegistry registry;

  public CommittedMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  /** 启动时预注册所有有限标签组合，让未发生的结果也以零值暴露。 */
  @PostConstruct
  void registerClosedSeries() {
    for (String outcome : MetricNames.INGEST_ROW_OUTCOMES) {
      registry.counter(MetricNames.INGEST_ROWS, MetricNames.TAG_OUTCOME, outcome);
    }
    for (String kind : WALLET_KINDS) {
      for (String outcome : MetricNames.WALLET_OUTCOMES) {
        registry.counter(
            MetricNames.WALLET_OPERATIONS,
            MetricNames.TAG_KIND,
            kind,
            MetricNames.TAG_OUTCOME,
            outcome);
      }
    }
    for (String event : MetricNames.PAYMENT_ORDER_EVENTS) {
      registry.counter(MetricNames.PAYMENT_ORDERS, MetricNames.TAG_EVENT, event);
    }
    registry.counter(
        MetricNames.CONTROL_PULL,
        MetricNames.TAG_PIPE,
        MetricNames.PIPE_PRICING,
        MetricNames.TAG_OUTCOME,
        MetricNames.OUTCOME_FULL);
    registry.counter(
        MetricNames.CONTROL_PULL,
        MetricNames.TAG_PIPE,
        MetricNames.PIPE_KEY_REVOCATIONS,
        MetricNames.TAG_OUTCOME,
        MetricNames.OUTCOME_FULL);
    for (String outcome : MetricNames.CONTROL_PULL_OUTCOMES) {
      registry.counter(
          MetricNames.CONTROL_PULL,
          MetricNames.TAG_PIPE,
          MetricNames.PIPE_TOPOLOGY,
          MetricNames.TAG_OUTCOME,
          outcome);
    }
  }

  /** 立即计数，不等待事务提交。 */
  public void count(String name, String... tags) {
    registry.counter(name, tags).increment();
  }

  /** 事务提交后计数；无活动事务同步时立即计数。 */
  public void countOnCommit(String name, String... tags) {
    Counter counter = registry.counter(name, tags);
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      counter.increment();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            counter.increment();
          }
        });
  }
}
