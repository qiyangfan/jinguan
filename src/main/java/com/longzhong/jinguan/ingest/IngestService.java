package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.common.CommittedMetrics;
import com.longzhong.jinguan.common.InsufficientFundsException;
import com.longzhong.jinguan.common.MetricNames;
import com.longzhong.jinguan.wallet.WalletService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 计量 ingest → 待结算事实 + 账本扣账:按 muniu UsageRow 的 APIKeyID 归属用户。 每行先校验身份与不可变事实并【可靠预留】明细(2xx =
 * 需要处理的用量已可靠落库,不表示全部已结算), 再经 WalletService(唯一资金通道,refId="usage:{ID}" 幂等)扣减——muniu 侧重投/缓冲续传天然幂等。 未绑定
 * key 的行留存计数(首次合法解析归属后才固定);失败行/零成本行不扣账; 余额不足的行留存为 skipped_insufficient(已固定归属的待结算事实,充值后由
 * UsageSettlementScheduler 周期恢复补扣,不依赖数据面重投)。
 */
@Service
public class IngestService {

  private final ApiKeyBindingRepository bindings;
  private final WalletService wallet;
  private final UsageRecordService usageRecords;
  private final CommittedMetrics metrics;

  public IngestService(
      ApiKeyBindingRepository bindings,
      WalletService wallet,
      UsageRecordService usageRecords,
      CommittedMetrics metrics) {
    this.bindings = bindings;
    this.wallet = wallet;
    this.usageRecords = usageRecords;
    this.metrics = metrics;
  }

  public record IngestResult(
      int received,
      int accepted,
      int replayed,
      int skippedUnbound,
      int skippedFree,
      int skippedInsufficient) {}

  /** 用量行 → 账本幂等键(muniu 重投/在线接收/后台恢复共用同一资金通道与同一 refId)。 */
  public static String usageRefId(String usageId) {
    return "usage:" + usageId;
  }

  /**
   * 逐行处理、单行失败不拖批:欠费行留存计数(skipped_insufficient)—— 整批回滚会让 muniu 侧重投永远命中同一毒丸行(本仓验收 17⑧ 同型教训)。
   * 事务形态:本方法【不开】类级事务——预留与 debit 各自经内部模板开独立事务,单行失败只影响该行;存储故障或同 ID 不同事实向上抛(非 2xx),此前已提交行不回滚。
   */
  public IngestResult accept(List<IngestUsageRow> rows) {
    int received = 0,
        accepted = 0,
        replayed = 0,
        skippedUnbound = 0,
        skippedFree = 0,
        skippedInsufficient = 0;
    for (IngestUsageRow row : rows) {
      received++;
      boolean billable = row.isSuccess() && row.CostCredits() > 0;
      Long boundUser =
          billable
              ? bindings.findById(row.APIKeyID()).map(ApiKeyBindingEntity::getUserId).orElse(null)
              : null;
      // 资金处理前可靠保存:失败/同 ID 不同事实 → 异常(非 2xx),数据面续传重试
      Long pinnedUser = usageRecords.reserve(row, boundUser, billable);
      if (!billable) {
        skippedFree++;
        countRow(MetricNames.OUTCOME_SKIPPED_FREE);
        continue;
      }
      if (pinnedUser == null) {
        skippedUnbound++;
        countRow(MetricNames.OUTCOME_SKIPPED_UNBOUND);
        continue;
      }
      try {
        var result =
            wallet.debit(
                pinnedUser,
                row.CostCredits(),
                WalletService.REASON_USAGE,
                usageRefId(row.ID()),
                "用量 " + row.Provider() + "/" + row.ModelAlias(),
                null);
        if (result.replayed()) {
          replayed++;
          usageRecords.applySettlementOutcome(row.ID(), UsageRecordService.OUTCOME_REPLAYED);
          countRow(MetricNames.OUTCOME_REPLAYED);
        } else {
          accepted++;
          usageRecords.applySettlementOutcome(row.ID(), UsageRecordService.OUTCOME_ACCEPTED);
          countRow(MetricNames.OUTCOME_ACCEPTED);
        }
      } catch (InsufficientFundsException e) {
        skippedInsufficient++;
        // 欠费停服联动已内聚 WalletService.debit(锁内置位,与并发入账串行);
        // 此处落欠费投影(已固定归属的待结算事实),充值后由恢复器补扣
        usageRecords.applySettlementOutcome(
            row.ID(), UsageRecordService.OUTCOME_SKIPPED_INSUFFICIENT);
        countRow(MetricNames.OUTCOME_SKIPPED_INSUFFICIENT);
      }
    }
    return new IngestResult(
        received, accepted, replayed, skippedUnbound, skippedFree, skippedInsufficient);
  }

  /**
   * 行归宿计数:逐行即计,不批末累积。
   *
   * <p>本方法【不开】类级事务、每行各自独立提交,而批内仍可能抛出非欠费异常(如同 refId 不同载荷的幂等冲突)中断循环—— 若在方法末尾统一计数,那些【已提交扣账】的行就永远不会进
   * {@code ingest.rows},与账本永久分叉, 「连续多轮 accepted=0」这类静默丢行的判据也会因此失真。received
   * 为各桶之和,不单独计。后台恢复不经本方法,不得伪造接收计数。
   */
  private void countRow(String outcome) {
    metrics.count(MetricNames.INGEST_ROWS, MetricNames.TAG_OUTCOME, outcome);
  }
}
