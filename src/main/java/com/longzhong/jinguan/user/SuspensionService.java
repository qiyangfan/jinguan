package com.longzhong.jinguan.user;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 欠费停服与同步水位(user 模块):停服是账号级服务状态——ingest 余额不足自动置位(幂等), 任何入账自动清除(与账本分录同事务,由 WalletService
 * 在锁内调用);置位/恢复的实际发生都必须与 key-revocations 水位 bump 同事务, 否则数据面(仅应用 version &gt;= 本地的快照)会把变化当旧快照忽略。
 *
 * <p>幂等语义:重复置位/重复恢复不产生状态变化,不 bump 水位;并发同键由条件 UPDATE( WHERE suspended_at IS NULL / IS NOT
 * NULL)原子裁决,恰一方生效。
 */
@Service
public class SuspensionService {

  /** key 吊销下发管道的水位名(后续管道按名复用 sync_watermarks)。 */
  public static final String WATERMARK_KEY_REVOCATIONS = "key-revocations";

  private final UserRepository users;
  private final SyncWatermarkRepository watermarks;

  public SuspensionService(UserRepository users, SyncWatermarkRepository watermarks) {
    this.users = users;
    this.watermarks = watermarks;
  }

  /** 欠费停服置位(幂等):仅在状态实际变化时 bump 水位。 */
  @Transactional
  public boolean markSuspended(long userId) {
    int updated = users.suspendIfClear(userId, Instant.now());
    if (updated > 0) {
      bumpKeyRevocationVersion();
      return true;
    }
    return false;
  }

  /** 停服恢复(幂等):必须与调用方(入账)同事务——不可能「入账成功但停服未解除」。 */
  @Transactional
  public boolean clearIfSuspended(long userId) {
    int updated = users.resumeIfSuspended(userId);
    if (updated > 0) {
      bumpKeyRevocationVersion();
      return true;
    }
    return false;
  }

  /** 当前全部欠费停服用户 id(下发名单派生用)。 */
  @Transactional(readOnly = true)
  public List<Long> suspendedUserIds() {
    return users.findSuspendedIds();
  }

  /** key-revocations 水位当前值(下发快照的 version 字段)。 */
  @Transactional(readOnly = true)
  public long keyRevocationVersion() {
    return watermarks.versionOf(WATERMARK_KEY_REVOCATIONS).orElse(0L);
  }

  /** 名单变化方(如终态吊销落库后)显式 bump;与状态变更须同事务调用。 */
  @Transactional
  public void bumpKeyRevocationVersion() {
    watermarks.bump(WATERMARK_KEY_REVOCATIONS);
  }
}
