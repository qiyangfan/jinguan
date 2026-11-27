package com.longzhong.jinguan.policy;

import java.time.Instant;
import java.util.List;

/** 策略包视图与按服务投影的快照。 */
public interface PolicyViews {

  /** 策略包视图。 */
  record PolicyPackageView(
      String id,
      String packageKey,
      String serviceId,
      String scope,
      String tenantKey,
      String activeRulesetVersion,
      String previousRulesetVersion,
      int activeVersion,
      String status,
      Instant updatedAt) {}

  /** 策略包引用。 */
  record PackageRef(
      String packageKey,
      String serviceId,
      String scope,
      String tenantKey,
      String rulesetVersion,
      Instant updatedAt) {}

  /** 策略包快照。 */
  record PolicySnapshot(int packageCount, List<PackageRef> packages) {

    public PolicySnapshot {
      packages = List.copyOf(packages);
    }
  }
}
