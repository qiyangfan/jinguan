package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.user.SuspensionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 写入终态吊销记录，并在同一事务中更新下发水位。 */
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

  /** 记录终态吊销并推进下发水位；返回 true 表示该 key 已吊销，按幂等重放处理。 */
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
      // 并发吊销同一 key 时重读胜方；其他完整性错误继续抛出。
      Boolean replayed = tx.execute(status -> revocations.existsByKeyId(keyId));
      if (Boolean.TRUE.equals(replayed)) {
        return true;
      }
      throw e;
    }
  }
}
