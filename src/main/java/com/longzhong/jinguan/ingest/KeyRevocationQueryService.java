package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.common.KeyRevokedException;
import com.longzhong.jinguan.user.SuspensionService;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** key 绑定登记与吊销名单快照的用例入口，控制器不直接触达仓储。 */
@Service
public class KeyRevocationQueryService {

  private final ApiKeyBindingRepository bindings;
  private final KeyRevocationRepository revocations;
  private final SuspensionService suspension;

  public KeyRevocationQueryService(
      ApiKeyBindingRepository bindings,
      KeyRevocationRepository revocations,
      SuspensionService suspension) {
    this.bindings = bindings;
    this.revocations = revocations;
    this.suspension = suspension;
  }

  /** 绑定 key 与用户；终态吊销的 key 拒绝重新绑定。 */
  @Transactional
  public void bind(String keyId, long userId) {
    if (revocations.existsByKeyId(keyId)) {
      throw new KeyRevokedException(keyId);
    }
    bindings.save(new ApiKeyBindingEntity(keyId, userId));
  }

  /** 汇总当前下发版本与吊销名单，包含终态吊销和停服用户的绑定 key。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Snapshot snapshot() {
    // 先读版本再读名单，避免返回新版本却携带旧名单。
    long version = suspension.keyRevocationVersion();
    var revoked = new TreeSet<>(revocations.findAllKeyIds());
    List<Long> suspended = suspension.suspendedUserIds();
    if (!suspended.isEmpty()) {
      bindings.findByUserIdIn(suspended).forEach(b -> revoked.add(b.getKeyId()));
    }
    return new Snapshot(version, List.copyOf(revoked));
  }

  /** 一次下发的内容：单调递增的版本号与按 keyId 排序的吊销名单；名单复制为不可变副本。 */
  public record Snapshot(long version, List<String> revoked) {

    public Snapshot {
      revoked = List.copyOf(revoked);
    }
  }
}
