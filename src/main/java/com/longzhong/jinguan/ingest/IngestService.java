package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.common.CommittedMetrics;
import com.longzhong.jinguan.common.InsufficientFundsException;
import com.longzhong.jinguan.common.MetricNames;
import com.longzhong.jinguan.wallet.WalletService;
import java.util.List;
import org.springframework.stereotype.Service;

/** 按 APIKeyID 找到归属用户，保存 UsageRow 后以 usage:{ID} 幂等扣账；欠费行由恢复任务补扣。 */
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

  /** 用量行对应的账本幂等键。 */
  public static String usageRefId(String usageId) {
    return "usage:" + usageId;
  }

  /** 每行独立提交；欠费行保留待结算，存储故障或事实冲突返回非 2xx。 */
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
      // 先可靠保存事实；失败时返回非 2xx，让数据面重试。
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
        usageRecords.applySettlementOutcome(
            row.ID(), UsageRecordService.OUTCOME_SKIPPED_INSUFFICIENT);
        countRow(MetricNames.OUTCOME_SKIPPED_INSUFFICIENT);
      }
    }
    return new IngestResult(
        received, accepted, replayed, skippedUnbound, skippedFree, skippedInsufficient);
  }

  /** 每处理一行即计数，避免批次中断时漏记已提交结果；后台恢复不计为新接收。 */
  private void countRow(String outcome) {
    metrics.count(MetricNames.INGEST_ROWS, MetricNames.TAG_OUTCOME, outcome);
  }
}
