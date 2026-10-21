package com.longzhong.jinguan.common;

import java.util.List;

/** 集中定义指标名和标签；标签使用有限取值以避免高基数序列。 */
public final class MetricNames {

  /** 计量接收行的处理结果。 */
  public static final String INGEST_ROWS = "jinguan.ingest.rows";

  /** 账本操作结果。 */
  public static final String WALLET_OPERATIONS = "jinguan.wallet.operations";

  /** 支付订单状态事件。 */
  public static final String PAYMENT_ORDERS = "jinguan.payment.orders";

  /** 下发端点拉取结果。 */
  public static final String CONTROL_PULL = "jinguan.control.pull";

  /** 已过期订单收敛总数。 */
  public static final String PAYMENT_EXPIRY_CONVERGED = "jinguan.payment.expiry.converged";

  /** 过期订单扫描耗时。 */
  public static final String PAYMENT_EXPIRY_SCAN = "jinguan.payment.expiry.scan";

  /** 最近一轮扫描后的过期未收敛订单数。 */
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

  /** 账本指标使用的标签键。 */
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

  /** 下发端点的完整响应与未修改响应。 */
  public static final List<String> CONTROL_PULL_OUTCOMES =
      List.of(OUTCOME_FULL, OUTCOME_NOT_MODIFIED);

  private MetricNames() {}
}
