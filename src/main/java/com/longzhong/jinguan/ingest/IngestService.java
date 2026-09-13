package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.common.InsufficientFundsException;
import com.longzhong.jinguan.wallet.WalletService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 计量 ingest → 账本扣账:按 muniu UsageRow 的 APIKeyID 归属用户,以 UsageRow.ID 为账本幂等键(refId)扣减——muniu
 * 侧重投/缓冲续传天然幂等。 未绑定 key 的行跳过并计数(无法归账;管理员补绑定后待 muniu 下轮投递)。 失败行/零成本行不扣账(计费语义与 muniu cost=0 一致)。
 * 余额不足的行跳过并计欠费停服(独立小事务幂等置位,数据面一个拉取周期内停止放行;充值后 WalletService 入账自动恢复)。
 */
@Service
public class IngestService {

  private final ApiKeyBindingRepository bindings;
  private final WalletService wallet;
  private final UsageRecordService usageRecords;

  public IngestService(
      ApiKeyBindingRepository bindings, WalletService wallet, UsageRecordService usageRecords) {
    this.bindings = bindings;
    this.wallet = wallet;
    this.usageRecords = usageRecords;
  }

  public record IngestResult(
      int received,
      int accepted,
      int replayed,
      int skippedUnbound,
      int skippedFree,
      int skippedInsufficient) {}

  /**
   * 逐行处理、单行失败不拖批:余额不足的行跳过并计数(skipped_insufficient)—— 整批回滚会让 muniu 侧重投永远命中同一毒丸行(本仓验收 17⑧ 同型教训), 充值后待
   * muniu 下轮投递自然补账。 事务形态:本方法【不开】类级事务——每行 debit 经 WalletService 内 REQUIRED
   * 模板各自开启独立事务,单行失败只回滚该行;批间隔离由账本 refId 幂等兜底。
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
      if (!row.isSuccess() || row.CostCredits() <= 0) {
        skippedFree++;
        usageRecords.record(row, UsageRecordService.OUTCOME_SKIPPED_FREE, null);
        continue;
      }
      var binding = bindings.findById(row.APIKeyID());
      if (binding.isEmpty()) {
        skippedUnbound++;
        usageRecords.record(row, UsageRecordService.OUTCOME_SKIPPED_UNBOUND, null);
        continue;
      }
      long boundUser = binding.get().getUserId();
      try {
        var result =
            wallet.debit(
                boundUser,
                row.CostCredits(),
                WalletService.REASON_USAGE,
                "usage:" + row.ID(),
                "用量 " + row.Provider() + "/" + row.ModelAlias(),
                null);
        if (result.replayed()) {
          replayed++;
          usageRecords.record(row, UsageRecordService.OUTCOME_REPLAYED, boundUser);
        } else {
          accepted++;
          usageRecords.record(row, UsageRecordService.OUTCOME_ACCEPTED, boundUser);
        }
      } catch (InsufficientFundsException e) {
        skippedInsufficient++;
        // 欠费停服联动已内聚 WalletService.debit(锁内置位,与并发入账串行);
        // 此处仅做明细投影:数据面最迟下一拉取周期(默认 60s)收敛停止放行
        usageRecords.record(row, UsageRecordService.OUTCOME_SKIPPED_INSUFFICIENT, boundUser);
      }
    }
    return new IngestResult(
        received, accepted, replayed, skippedUnbound, skippedFree, skippedInsufficient);
  }
}
