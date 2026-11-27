package com.longzhong.jinguan.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 目录条目:只引用运行时 Bot 标识与运营字段,不复制 Bot 定义/版本。 */
@Entity
@Table(name = "catalog_entries")
public class CatalogEntryEntity {

  @Id
  @Column(nullable = false, length = 36)
  private String id;

  @Column(name = "entry_key", nullable = false, unique = true, length = 64)
  private String entryKey;

  @Column(name = "bot_key", nullable = false, length = 128)
  private String botKey;

  @Column(name = "bot_tenant_key", length = 64)
  private String botTenantKey;

  @Column(nullable = false, length = 128)
  private String title;

  @Column(length = 512)
  private String summary;

  @Column(nullable = false, length = 16)
  private String visibility;

  @Column(name = "tenant_id", length = 36)
  private String tenantId;

  @Column(name = "entitlement_key", length = 128)
  private String entitlementKey;

  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false)
  private boolean featured;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA 无参构造。 */
  protected CatalogEntryEntity() {}

  static CatalogEntryEntity create(
      String entryKey,
      String botKey,
      String botTenantKey,
      String title,
      String summary,
      String visibility,
      String tenantId,
      String entitlementKey,
      boolean featured) {
    CatalogEntryEntity entity = new CatalogEntryEntity();
    entity.id = UUID.randomUUID().toString();
    entity.entryKey = entryKey;
    entity.botKey = botKey;
    entity.botTenantKey = botTenantKey;
    entity.title = title;
    entity.summary = summary;
    entity.visibility = visibility;
    entity.tenantId = tenantId;
    entity.entitlementKey = entitlementKey;
    entity.enabled = true;
    entity.featured = featured;
    entity.createdAt = Instant.now();
    entity.updatedAt = entity.createdAt;
    return entity;
  }

  void update(
      String nextTitle,
      String nextSummary,
      String nextVisibility,
      String nextTenantId,
      String nextEntitlementKey,
      boolean nextFeatured) {
    this.title = nextTitle;
    this.summary = nextSummary;
    this.visibility = nextVisibility;
    this.tenantId = nextTenantId;
    this.entitlementKey = nextEntitlementKey;
    this.featured = nextFeatured;
    this.updatedAt = Instant.now();
  }

  void changeEnabled(boolean value) {
    this.enabled = value;
    this.updatedAt = Instant.now();
  }

  public String getId() {
    return id;
  }

  public String getEntryKey() {
    return entryKey;
  }

  public String getBotKey() {
    return botKey;
  }

  public String getBotTenantKey() {
    return botTenantKey;
  }

  public String getTitle() {
    return title;
  }

  public String getSummary() {
    return summary;
  }

  public String getVisibility() {
    return visibility;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getEntitlementKey() {
    return entitlementKey;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isFeatured() {
    return featured;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
