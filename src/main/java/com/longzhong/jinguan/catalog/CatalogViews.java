package com.longzhong.jinguan.catalog;

import java.time.Instant;
import java.util.List;

/** 目录视图与快照:availability 由服务投影推导,不伪造。 */
public interface CatalogViews {

  /** 目录条目视图(含可用性)。 */
  record CatalogEntryView(
      String id,
      String entryKey,
      String botKey,
      String botTenantKey,
      String title,
      String summary,
      String visibility,
      String tenantKey,
      String entitlementKey,
      boolean enabled,
      boolean featured,
      String availability,
      Instant updatedAt) {}

  /** 目录快照条目引用。 */
  record CatalogEntryRef(
      String entryKey,
      String botKey,
      String visibility,
      String tenantKey,
      String entitlementKey,
      boolean featured) {}

  /** 目录快照。 */
  record CatalogSnapshot(int entryCount, List<CatalogEntryRef> entries) {

    public CatalogSnapshot {
      entries = List.copyOf(entries);
    }
  }

  /** 引用可用性:AVAILABLE/MISSING/UNKNOWN(投影缺失时不猜测)。 */
  enum Availability {
    AVAILABLE,
    MISSING,
    UNKNOWN
  }
}
