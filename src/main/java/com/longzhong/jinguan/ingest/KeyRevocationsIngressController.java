package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.user.SuspensionService;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 吊销下发契约端点(入站适配器):muniu revocationLoop 按 60s 周期拉取(启动即拉,无认证头靠部署网络隔离, 双方演进项)。
 *
 * <p>契约(internal/auth/auth.go 逐字段对齐):{"version":int64,"revoked":["key_id",..]},
 * <b>全量快照</b>(非增量,muniu 整集替换),version 单调(muniu 仅应用 version &gt;= 本地的快照)。 名单 =
 * 终态吊销(key_revocations)∪ 欠费停服用户名下全部绑定 key;排序保确定性。空态 {"version":0,"revoked":[]}.
 */
@RestController
public class KeyRevocationsIngressController {

  private final KeyRevocationRepository revocations;
  private final ApiKeyBindingRepository bindings;
  private final SuspensionService suspension;

  public KeyRevocationsIngressController(
      KeyRevocationRepository revocations,
      ApiKeyBindingRepository bindings,
      SuspensionService suspension) {
    this.revocations = revocations;
    this.bindings = bindings;
    this.suspension = suspension;
  }

  @GetMapping("/control/v1/key-revocations")
  public Map<String, Object> revocations() {
    // 先读版本后读名单:撕裂只可能「多报」(名单不落后于 version)——version 单调契约下,
    // 反序会产出「新版本+旧名单」,数据面应用后把已吊销 key 洗白
    long version = suspension.keyRevocationVersion();
    TreeSet<String> revoked = new TreeSet<>(revocations.findAllKeyIds());
    List<Long> suspended = suspension.suspendedUserIds();
    if (!suspended.isEmpty()) {
      bindings.findByUserIdIn(suspended).forEach(b -> revoked.add(b.getKeyId()));
    }
    return Map.of("version", version, "revoked", List.copyOf(revoked));
  }
}
