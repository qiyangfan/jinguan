package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.user.SuspensionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * key 终态吊销应用服务:insert 与水位 bump 必须单事务原子——漏 bump 会被数据面(version 单调, 仅应用 version &gt;=
 * 本地的快照)把新名单当旧快照忽略, 吊销永不下发。并发重复吊销撞唯一约束 → 新事务重读按幂等重放(重放不重复 bump:幂等=状态无变化), 非重复键的完整性失败如实上抛(不得假 200)。
 */
@Service
public class KeyRevocationService {

  private final KeyRevocationRepository revocations;
  private final SuspensionService suspension;
  private final TransactionTemplate tx;

  public KeyRevocationService(
      KeyRevocationRepository revocations,
      SuspensionService suspension,
      org.springframework.transaction.PlatformTransactionManager tm) {
    this.revocations = revocations;
    this.suspension = suspension;
    this.tx = new TransactionTemplate(tm);
  }

  /**
   * @return replayed=true 表示该 key 此前已吊销(幂等重放,状态无变化不重复 bump)。
   */
  public boolean revoke(final String keyId, final String reason, final Long operatorUserId) {
    try {
      return Boolean.TRUE.equals(
          tx.execute(
              status -> {
                if (revocations.existsByKeyId(keyId)) {
                  return true;
                }
                revocations.save(KeyRevocationEntity.of(keyId, reason, operatorUserId));
                suspension.bumpKeyRevocationVersion();
                return false;
              }));
    } catch (DataIntegrityViolationException e) {
      // 并发同键:唯一约束败方的事务已 rollback-only,新事务重读裁决;
      // 若重读仍不存在(列截断等非重复键完整性失败)则如实抛出,不得吞成幂等成功
      Boolean replayed = tx.execute(status -> revocations.existsByKeyId(keyId));
      if (Boolean.TRUE.equals(replayed)) {
        return true;
      }
      throw e;
    }
  }
}
