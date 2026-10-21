package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.common.IdempotencyConflictException;
import com.longzhong.jinguan.common.InsufficientFundsException;
import com.longzhong.jinguan.wallet.WalletService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 分批重试已保存的用量扣费：按最近尝试时间轮转用户、按接收顺序处理行；余额不足时跳过该用户。 单实例运行，只处理已固定归属且未结算的行。 */
@Component
public class UsageSettlementScheduler {

  private static final Logger log = LoggerFactory.getLogger(UsageSettlementScheduler.class);

  private final UsageRecordRepository records;
  private final UsageRecordService usageService;
  private final WalletService wallet;
  private final int maxUsers;
  private final int maxRows;

  public UsageSettlementScheduler(
      UsageRecordRepository records,
      UsageRecordService usageService,
      WalletService wallet,
      @Value("${jinguan.settlement.max-users:100}") int maxUsers,
      @Value("${jinguan.settlement.max-rows-per-user:100}") int maxRows) {
    this.records = records;
    this.usageService = usageService;
    this.wallet = wallet;
    this.maxUsers = maxUsers;
    this.maxRows = maxRows;
  }

  @jakarta.annotation.PostConstruct
  void validateBounds() {
    if (maxUsers <= 0 || maxRows <= 0) {
      throw new IllegalStateException(
          "jinguan.settlement.max-users/max-rows-per-user 必须为正: " + maxUsers + "/" + maxRows);
    }
  }

  @Scheduled(
      fixedDelayString = "${jinguan.settlement.fixed-delay-ms:30000}",
      initialDelayString = "${jinguan.settlement.initial-delay-ms:30000}")
  public void recoverPendingUsage() {
    recoverOnce();
  }

  /** 执行一轮恢复并汇总结果。 */
  public RecoveryReport recoverOnce() {
    List<Long> candidateUsers = records.findCandidateUsers(maxUsers);
    int settled = 0;
    int stillInsufficient = 0;
    int failed = 0;
    for (Long userId : candidateUsers) {
      List<UsageRecordEntity> rows = records.findCandidateRows(userId, maxRows);
      for (UsageRecordEntity row : rows) {
        // 先记录尝试，再更新结算结果。
        usageService.bumpRecoveryAttempt(row.getUsageId());
        try {
          var result =
              wallet.debit(
                  row.getUserId(),
                  row.getCostCredits(),
                  WalletService.REASON_USAGE,
                  IngestService.usageRefId(row.getUsageId()),
                  "用量结算 " + row.getModelAlias(),
                  null);
          usageService.applySettlementOutcome(
              row.getUsageId(),
              result.replayed()
                  ? UsageRecordService.OUTCOME_REPLAYED
                  : UsageRecordService.OUTCOME_ACCEPTED);
          settled++;
        } catch (InsufficientFundsException e) {
          usageService.applySettlementOutcome(
              row.getUsageId(), UsageRecordService.OUTCOME_SKIPPED_INSUFFICIENT);
          stillInsufficient++;
          break;
        } catch (IdempotencyConflictException e) {
          // 保留冲突行供后续处理。
          usageService.touchSettlementAttempt(row.getUsageId());
          log.error(
              "待结算行幂等冲突: usageId={} userId={} refId={}",
              row.getUsageId(),
              row.getUserId(),
              IngestService.usageRefId(row.getUsageId()),
              e);
          failed++;
        } catch (RuntimeException e) {
          // 单行异常不应中断整轮。
          log.error(
              "待结算行处理异常: usageId={} userId={} cost={}",
              row.getUsageId(),
              row.getUserId(),
              row.getCostCredits(),
              e);
          failed++;
        }
      }
    }
    if (settled + stillInsufficient + failed > 0) {
      log.info(
          "待结算恢复轮完成: 用户数={} 结算={} 仍欠费={} 失败={}",
          candidateUsers.size(),
          settled,
          stillInsufficient,
          failed);
    }
    return new RecoveryReport(candidateUsers.size(), settled, stillInsufficient, failed);
  }

  public record RecoveryReport(int usersTouched, int settled, int stillInsufficient, int failed) {}
}
