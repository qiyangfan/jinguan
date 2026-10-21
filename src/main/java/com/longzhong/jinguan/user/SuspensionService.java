package com.longzhong.jinguan.user;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 管理欠费停服状态；状态变化与 key-revocations 水位更新保持在同一事务中。 */
@Service
public class SuspensionService {

  /** key 吊销下发管道的水位名。 */
  public static final String WATERMARK_KEY_REVOCATIONS = "key-revocations";

  private final UserRepository users;
  private final SyncWatermarkRepository watermarks;

  public SuspensionService(UserRepository users, SyncWatermarkRepository watermarks) {
    this.users = users;
    this.watermarks = watermarks;
  }

  /** 在独立事务中标记停服，避免调用方回滚时丢失欠费状态。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markSuspended(long userId) {
    int updated = users.suspendIfClear(userId, Instant.now());
    if (updated > 0) {
      bumpKeyRevocationVersion();
      return true;
    }
    return false;
  }

  /** 与入账同事务恢复停服。 */
  @Transactional
  public boolean clearIfSuspended(long userId) {
    int updated = users.resumeIfSuspended(userId);
    if (updated > 0) {
      bumpKeyRevocationVersion();
      return true;
    }
    return false;
  }

  /** 查询全部停服用户 ID。 */
  @Transactional(readOnly = true)
  public List<Long> suspendedUserIds() {
    return users.findSuspendedIds();
  }

  /** 查询 key-revocations 下发版本。 */
  @Transactional(readOnly = true)
  public long keyRevocationVersion() {
    return watermarks.versionOf(WATERMARK_KEY_REVOCATIONS).orElse(0L);
  }

  /** 增加 key-revocations 水位；须与名单变更同事务调用。 */
  @Transactional
  public void bumpKeyRevocationVersion() {
    watermarks.bump(WATERMARK_KEY_REVOCATIONS);
  }
}
