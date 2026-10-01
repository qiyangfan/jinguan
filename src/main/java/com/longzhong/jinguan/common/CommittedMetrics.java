package com.longzhong.jinguan.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 指标计数装配点:区分「事实已提交」与「事实与事务无关」两类语义。
 *
 * <p>资金分录是否生效必须以【提交】为准——回调链路的账本入账参与外层事务(BEFORE_COMMIT), 外层回滚时分录并不存在;若在方法返回处直接计数,指标会虚增成一笔不存在的资金事实。
 * 拒绝类事件(余额不足、同键冲突)相反:被拒绝本身就是终态事实,回滚不改变它,故立即计。
 *
 * <p>指标词汇表(名称/标签)一律引用 {@link MetricNames},禁止在业务类里写字符串字面量。
 */
@Component
public class CommittedMetrics {

  private static final List<String> WALLET_KINDS =
      List.of(MetricNames.KIND_CREDIT, MetricNames.KIND_DEBIT);

  private final MeterRegistry registry;

  public CommittedMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  /**
   * 启动期预建封闭取值的全部序列(值为 0)。
   *
   * <p>Prometheus 的 {@code increase(...)==0} 型判据对【不存在】的序列返回空向量,比较根本不成立—— 也就是说「重启后 100%
   * skipped、accepted 一路为零」这种最该告警的形态会静默。序列必须在零值时就存在, 判据才有东西可比。
   *
   * <p>零值序列是词汇表声明,不是可达性声明(如 {@code credit{rejected_insufficient}} 现实中不出现)。
   */
  @PostConstruct
  void registerClosedSeries() {
    for (String outcome : MetricNames.INGEST_ROW_OUTCOMES) {
      registry.counter(
          MetricNames.INGEST_ROWS, MetricNames.TAG_OUTCOME, outcome); // 预建零值,使 ==0 判据可比
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
      registry.counter(MetricNames.PAYMENT_ORDERS, MetricNames.TAG_EVENT, event); // 预建零值,使 ==0 判据可比
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

  /**
   * 立即计数:与事务提交无关的事实(行归宿分桶、端点被调用、拒绝事件)。
   *
   * <p>批量增量不在此提供:批末统一推进会让批内异常吞掉已提交事实的计数(见 {@code IngestService#countRow}),同类事实必须逐件计。
   */
  public void count(String name, String... tags) {
    registry.counter(name, tags).increment();
  }

  /** 提交后计数:当前有事务同步在跑时挂到 afterCommit(回滚则永不计数), 无活动事务(独立事务已提交)等同 {@link #count}。 */
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
