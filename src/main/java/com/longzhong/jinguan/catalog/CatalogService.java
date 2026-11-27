package com.longzhong.jinguan.catalog;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import com.longzhong.jinguan.common.ConflictException;
import com.longzhong.jinguan.common.NotFoundException;
import com.longzhong.jinguan.controlversion.ControlVersionService;
import com.longzhong.jinguan.tenant.TenantQueryService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 商店目录运营:引用运行时 Bot,不可用显示 MISSING,不静默隐藏。 */
@Service
public class CatalogService {

  private static final String TARGET = "catalog-entry";
  private static final String ENTRY_MISSING = "目录条目不存在: ";

  private final CatalogEntryRepository entries;
  private final TenantQueryService tenants;
  private final CatalogValidation validation;
  private final CatalogAvailability availability;
  private final ControlVersionService versions;
  private final AuditRecorder recorder;

  public CatalogService(
      CatalogEntryRepository entries,
      TenantQueryService tenants,
      CatalogValidation validation,
      CatalogAvailability availability,
      ControlVersionService versions,
      AuditRecorder recorder) {
    this.entries = entries;
    this.tenants = tenants;
    this.validation = validation;
    this.availability = availability;
    this.versions = versions;
    this.recorder = recorder;
  }

  /** 目录写入参数。 */
  public record EntryInput(
      String title,
      String summary,
      String visibility,
      String tenantId,
      String entitlementKey,
      boolean featured) {}

  @SuppressWarnings("PMD.UseObjectForClearerAPI") // 参数均为必填的独立字段,容器对象只增样板
  @Transactional
  public CatalogViews.CatalogEntryView create(
      String entryKey,
      String botKey,
      String botTenantKey,
      EntryInput input,
      String reason,
      String actorSubject,
      String actorRole) {
    validation.requireReason(reason);
    validation.requireEntryKey(entryKey);
    if (entries.existsByEntryKey(entryKey)) {
      throw new ConflictException("目录条目已存在: " + entryKey);
    }
    String normalizedBotKey = validation.requireBotKey(botKey);
    CatalogValidation.Normalized normalized = validation.normalize(input);
    CatalogEntryEntity entity =
        entries.save(
            CatalogEntryEntity.create(
                entryKey,
                normalizedBotKey,
                botTenantKey,
                normalized.title(),
                normalized.summary(),
                normalized.visibility(),
                normalized.tenantId(),
                normalized.entitlementKey(),
                input.featured()));
    long version = versions.bump();
    recorder.record(
        AuditActor.user(actorSubject, actorRole),
        AuditActions.CATALOG_CREATE,
        TARGET,
        entity.getId(),
        normalized.tenantKey(),
        reason,
        Map.of(
            "entryKey", entryKey,
            "botKey", normalizedBotKey,
            "visibility", normalized.visibility(),
            "version", version),
        "SUCCESS");
    return viewOf(entity, availability.load());
  }

  @SuppressWarnings("PMD.UseObjectForClearerAPI") // 参数均为必填的独立字段,容器对象只增样板
  @Transactional
  public CatalogViews.CatalogEntryView update(
      String id, EntryInput input, String reason, String actorSubject, String actorRole) {
    validation.requireReason(reason);
    CatalogEntryEntity entity = requireEntry(id);
    CatalogValidation.Normalized normalized = validation.normalize(input);
    entity.update(
        normalized.title(),
        normalized.summary(),
        normalized.visibility(),
        normalized.tenantId(),
        normalized.entitlementKey(),
        input.featured());
    entries.save(entity);
    long version = versions.bump();
    recorder.record(
        AuditActor.user(actorSubject, actorRole),
        AuditActions.CATALOG_UPDATE,
        TARGET,
        id,
        normalized.tenantKey(),
        reason,
        Map.of("entryKey", entity.getEntryKey(), "version", version),
        "SUCCESS");
    return viewOf(entity, availability.load());
  }

  @SuppressWarnings("PMD.UseObjectForClearerAPI") // 参数均为必填的独立字段,容器对象只增样板
  @Transactional
  public CatalogViews.CatalogEntryView changeEnabled(
      String id, boolean enabled, String reason, String actorSubject, String actorRole) {
    validation.requireReason(reason);
    CatalogEntryEntity entity = requireEntry(id);
    if (entity.isEnabled() != enabled) {
      entity.changeEnabled(enabled);
      entries.save(entity);
      versions.bump();
      recorder.record(
          AuditActor.user(actorSubject, actorRole),
          enabled ? AuditActions.CATALOG_UPDATE : AuditActions.CATALOG_DISABLE,
          TARGET,
          id,
          null,
          reason,
          Map.of("entryKey", entity.getEntryKey(), "enabled", enabled),
          "SUCCESS");
    }
    return viewOf(entity, availability.load());
  }

  @Transactional
  public List<CatalogViews.CatalogEntryView> list() {
    CatalogAvailability.Index index = availability.load();
    return entries.findAll().stream()
        .sorted(java.util.Comparator.comparing(CatalogEntryEntity::getEntryKey))
        .map(entity -> viewOf(entity, index))
        .toList();
  }

  /** kongming 货架消费的快照:仅启用的条目。 */
  @Transactional(readOnly = true)
  public CatalogViews.CatalogSnapshot snapshot() {
    List<CatalogViews.CatalogEntryRef> refs =
        entries.findAll().stream()
            .filter(CatalogEntryEntity::isEnabled)
            .sorted(java.util.Comparator.comparing(CatalogEntryEntity::getEntryKey))
            .map(
                entity ->
                    new CatalogViews.CatalogEntryRef(
                        entity.getEntryKey(),
                        entity.getBotKey(),
                        entity.getVisibility(),
                        tenantKeyOf(entity),
                        entity.getEntitlementKey(),
                        entity.isFeatured()))
            .toList();
    return new CatalogViews.CatalogSnapshot(refs.size(), refs);
  }

  private CatalogViews.CatalogEntryView viewOf(
      CatalogEntryEntity entity, CatalogAvailability.Index index) {
    return new CatalogViews.CatalogEntryView(
        entity.getId(),
        entity.getEntryKey(),
        entity.getBotKey(),
        entity.getBotTenantKey(),
        entity.getTitle(),
        entity.getSummary(),
        entity.getVisibility(),
        tenantKeyOf(entity),
        entity.getEntitlementKey(),
        entity.isEnabled(),
        entity.isFeatured(),
        availability.of(entity.getBotKey(), index).name(),
        entity.getUpdatedAt());
  }

  private String tenantKeyOf(CatalogEntryEntity entity) {
    return entity.getTenantId() == null ? null : tenants.get(entity.getTenantId()).tenantKey();
  }

  private CatalogEntryEntity requireEntry(String id) {
    return entries.findById(id).orElseThrow(() -> new NotFoundException(ENTRY_MISSING + id));
  }
}
