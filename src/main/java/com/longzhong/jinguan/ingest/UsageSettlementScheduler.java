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

/**
 * 待结算恢复器(单实例周期扫描):从已可靠落库且固定归属的 pending/欠费用量继续扣款—— 覆盖「充值后补扣、重启漏处理、扣账成功后投影更新失败」,不依赖数据面重投业务欠费行。
 *
 * <p>有界与公平:每轮至多 maxUsers 个用户、每用户至多 maxRows
 * 行;候选用户按「全部固定归属明细的最大结算尝试时间」升序轮转(从未尝试者优先),成功退出候选的行其尝试历史仍参与排序,长期欠费账户不占满首批。 每用户按首次接收顺序(id
 * 升序)处理,余额不足即止于该行、其他用户继续。候选读取为短事务,一切扣款经 WalletService 的每用户锁与完整资金事务裁决,与在线 ingest 并发以同一 refId
 * 收敛。fixedDelay 保证单线程内轮次不重叠;多实例部署需先行解决(见 README 集群化演进项)。
 *
 * <p>边界:未绑定(skipped_unbound)与免费(skipped_free)历史不由本器追收;已结算终态(accepted/replayed)不是候选; 本器不伪造 ingest
 * 接收计数(资金事实由 WalletService 指标承载)。
 */
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

  /** 配置异常启动即拒绝(生命周期回调,不带病进入无限扫描)。 */
  @jakarta.annotation.PostConstruct
  void validateBounds() {
    if (maxUsers <= 0 || maxRows <= 0) {
      throw new IllegalStateException(
          "jinguan.settlement.max-users/max-rows-per-user 必须为正: " + maxUsers + "/" + maxRows);
    }
  }

  /** 生产入口:启动后执行首轮,其后固定间隔轮询(fixedDelay = 上一轮完成后再计时,天然不重叠)。 */
  @Scheduled(
      fixedDelayString = "${jinguan.settlement.fixed-delay-ms:30000}",
      initialDelayString = "${jinguan.settlement.initial-delay-ms:30000}")
  public void recoverPendingUsage() {
    recoverOnce();
  }

  /** 单轮恢复(测试驱动入口):返回本轮统计,便于验收与日志对账。 */
  public RecoveryReport recoverOnce() {
    List<Long> candidateUsers = records.findCandidateUsers(maxUsers);
    int settled = 0;
    int stillInsufficient = 0;
    int failed = 0;
    for (Long userId : candidateUsers) {
      List<UsageRecordEntity> rows = records.findCandidateRows(userId, maxRows);
      for (UsageRecordEntity row : rows) {
        // 恢复尝试计数(attempts/last_attempt_at),与结算结果落投影分离、不双计
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
          break; // 该用户本轮到此为止(余额不足),其他用户继续
        } catch (IdempotencyConflictException e) {
          // 事实已核对仍冲突 = 同 refId 存在不同载荷分录的数据不一致;保留候选、高噪留痕,不吞异常成因
          usageService.touchSettlementAttempt(row.getUsageId());
          log.error(
              "待结算行幂等冲突: usageId={} userId={} refId={}",
              row.getUsageId(),
              row.getUserId(),
              IngestService.usageRefId(row.getUsageId()),
              e);
          failed++;
        } catch (RuntimeException e) {
          // 非预期异常兜底(如坏数据 cost≤0 触发金额校验):毒行不得炸掉整轮——
          // 规格要求单个问题用户 SHALL NOT 导致其他用户永久饥饿;保留候选、带堆栈留痕,不吞成因
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

  /** 单轮统计(验收/测试对账用):failed = 幂等冲突行 + 非预期异常行(均保留候选、留痕)。 */
  public record RecoveryReport(int usersTouched, int settled, int stillInsufficient, int failed) {}
}
