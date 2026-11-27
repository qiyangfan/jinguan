package com.longzhong.jinguan.catalog;

import com.longzhong.jinguan.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 目录运营管理;写操作要求原因并审计。 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class CatalogAdminController {

  private final CatalogService catalog;

  public CatalogAdminController(CatalogService catalog) {
    this.catalog = catalog;
  }

  /** 创建目录条目请求。 */
  public record EntryCreate(
      @NotBlank String entryKey,
      @NotBlank String botKey,
      String botTenantKey,
      @NotBlank String title,
      String summary,
      @NotBlank String visibility,
      String tenantId,
      String entitlementKey,
      Boolean featured,
      @NotBlank String reason) {}

  /** 更新目录条目请求。 */
  public record EntryUpdate(
      @NotBlank String title,
      String summary,
      @NotBlank String visibility,
      String tenantId,
      String entitlementKey,
      Boolean featured,
      @NotBlank String reason) {}

  /** 带原因的写操作请求。 */
  public record ReasonRequest(@NotBlank String reason) {}

  @GetMapping("/admin/catalog")
  public List<CatalogViews.CatalogEntryView> list() {
    return catalog.list();
  }

  @PostMapping("/admin/catalog")
  public CatalogViews.CatalogEntryView create(
      @Valid @RequestBody EntryCreate request,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    return catalog.create(
        request.entryKey(),
        request.botKey(),
        request.botTenantKey(),
        inputOf(
            request.title(),
            request.summary(),
            request.visibility(),
            request.tenantId(),
            request.entitlementKey(),
            Boolean.TRUE.equals(request.featured())),
        request.reason(),
        actor.subject(),
        actor.role());
  }

  @PutMapping("/admin/catalog/{id}")
  public CatalogViews.CatalogEntryView update(
      @PathVariable String id,
      @Valid @RequestBody EntryUpdate request,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    return catalog.update(
        id,
        inputOf(
            request.title(),
            request.summary(),
            request.visibility(),
            request.tenantId(),
            request.entitlementKey(),
            Boolean.TRUE.equals(request.featured())),
        request.reason(),
        actor.subject(),
        actor.role());
  }

  @PostMapping("/admin/catalog/{id}/disable")
  public CatalogViews.CatalogEntryView disable(
      @PathVariable String id,
      @Valid @RequestBody ReasonRequest request,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    return catalog.changeEnabled(id, false, request.reason(), actor.subject(), actor.role());
  }

  @PostMapping("/admin/catalog/{id}/enable")
  public CatalogViews.CatalogEntryView enable(
      @PathVariable String id,
      @Valid @RequestBody ReasonRequest request,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    return catalog.changeEnabled(id, true, request.reason(), actor.subject(), actor.role());
  }

  private static CatalogService.EntryInput inputOf(
      String title,
      String summary,
      String visibility,
      String tenantId,
      String entitlementKey,
      boolean featured) {
    return new CatalogService.EntryInput(
        title, summary, visibility, tenantId, entitlementKey, featured);
  }
}
