package com.longzhong.jinguan.common;

import java.util.List;

/**
 * 运维指标词汇表单源:指标名、标签键与封闭标签值一律在此登记。
 *
 * <p>纪律:新增业务计数器必须先在此登记,并同步 {@code docs/design/ops-metrics.md} 的指标表与 {@code
 * OpsMetricsIntegrationTest} 断言——文档抄错可被测试反查。同一资金事实只允许一个序列承载 (例:过期收敛已由 {@link
 * #PAYMENT_EXPIRY_CONVERGED} 表达,不得再造 {@code orders{event=expired}})。
 *
 * <p>禁止 (MUST NOT) 把用户 ID、订单号、refId、厂商名等无界取值用作标签:时间序列基数会失控; 按用户/订单的读数由账本历史与用量明细端点承担。
 */
public final class MetricNames {

  /** 计量接收行的归宿(桶语义与 {@code IngestService.IngestResult} 同源;received 为各桶之和,不单独计)。 */
  public static final String INGEST_ROWS = "jinguan.ingest.rows";

  /** 账本写入事实,计数点内聚在唯一资金通道 {@code WalletService} 内。 */
  public static final String WALLET_OPERATIONS = "jinguan.wallet.operations";

  /** 支付单状态迁移(仅 created/paid;过期事实见 {@link #PAYMENT_EXPIRY_CONVERGED})。 */
  public static final String PAYMENT_ORDERS = "jinguan.payment.orders";

  /** 拉取型下发管子的活性(计数点在端点层,管理页复用 service 不计)。 */
  public static final String CONTROL_PULL = "jinguan.control.pull";

  /** 过期收敛累计笔数(既有名称,保持不重命名)。 */
  public static final String PAYMENT_EXPIRY_CONVERGED = "jinguan.payment.expiry.converged";

  /** 过期收敛扫描耗时(既有名称)。 */
  public static final String PAYMENT_EXPIRY_SCAN = "jinguan.payment.expiry.scan";

  /** 最近一轮已过期未收敛存量(Gauge;兑现扫描器类注释声称的暴露面)。 */
  public static final String PAYMENT_EXPIRY_BACKLOG = "jinguan.payment.expiry.backlog";

  // ---- 标签键 ----
  public static final String TAG_OUTCOME = "outcome";
  public static final String TAG_KIND = "kind";
  public static final String TAG_EVENT = "event";
  public static final String TAG_PIPE = "pipe";

  // ---- 标签值(封闭枚举) ----
  public static final String OUTCOME_APPLIED = "applied";
  public static final String OUTCOME_REPLAYED = "replayed";
  public static final String OUTCOME_REJECTED_INSUFFICIENT = "rejected_insufficient";
  public static final String OUTCOME_REJECTED_CONFLICT = "rejected_conflict";
  public static final String OUTCOME_ACCEPTED = "accepted";
  public static final String OUTCOME_SKIPPED_UNBOUND = "skipped_unbound";
  public static final String OUTCOME_SKIPPED_FREE = "skipped_free";
  public static final String OUTCOME_SKIPPED_INSUFFICIENT = "skipped_insufficient";
  public static final String OUTCOME_FULL = "full";
  public static final String OUTCOME_NOT_MODIFIED = "not_modified";

  public static final String KIND_CREDIT = "credit";
  public static final String KIND_DEBIT = "debit";

  public static final String EVENT_CREATED = "created";
  public static final String EVENT_PAID = "paid";

  public static final String PIPE_PRICING = "pricing";
  public static final String PIPE_KEY_REVOCATIONS = "key_revocations";
  public static final String PIPE_TOPOLOGY = "topology";

  /** 账本指标的合法标签集(测试按此断言无高基数标签泄漏)。 */
  public static final List<String> WALLET_TAG_KEYS = List.of("application", TAG_KIND, TAG_OUTCOME);

  // ---- 封闭取值表(启动期据此预建零值序列,见 CommittedMetrics#registerClosedSeries) ----

  public static final List<String> INGEST_ROW_OUTCOMES =
      List.of(
          OUTCOME_ACCEPTED,
          OUTCOME_REPLAYED,
          OUTCOME_SKIPPED_UNBOUND,
          OUTCOME_SKIPPED_FREE,
          OUTCOME_SKIPPED_INSUFFICIENT);

  public static final List<String> WALLET_OUTCOMES =
      List.of(
          OUTCOME_APPLIED,
          OUTCOME_REPLAYED,
          OUTCOME_REJECTED_INSUFFICIENT,
          OUTCOME_REJECTED_CONFLICT);

  public static final List<String> PAYMENT_ORDER_EVENTS = List.of(EVENT_CREATED, EVENT_PAID);

  /** topology 有 ETag 条件请求,另两管恒整包。 */
  public static final List<String> CONTROL_PULL_OUTCOMES =
      List.of(OUTCOME_FULL, OUTCOME_NOT_MODIFIED);

  private MetricNames() {}
}
