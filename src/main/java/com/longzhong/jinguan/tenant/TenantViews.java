package com.longzhong.jinguan.tenant;

import java.time.Instant;
import java.util.List;

/** 供控制快照与服务授权消费的租户目录视图。 */
public interface TenantViews {

  /** 租户成员视图。 */
  record MemberView(String subject, String role, String status) {}

  /** 租户视图(含成员)。 */
  record TenantView(
      String id,
      String tenantKey,
      String name,
      String status,
      Instant updatedAt,
      List<MemberView> members) {

    public TenantView {
      members = List.copyOf(members);
    }
  }

  /** 租户目录快照。 */
  record TenantDirectorySnapshot(long tenantCount, List<TenantView> tenants) {

    public TenantDirectorySnapshot {
      tenants = List.copyOf(tenants);
    }
  }
}
