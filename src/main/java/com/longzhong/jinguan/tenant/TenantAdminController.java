package com.longzhong.jinguan.tenant;

import com.longzhong.jinguan.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 租户/成员管理;全部写操作要求原因,服务端记录审计。 */
@RestController
@RequestMapping("/admin/tenants")
@PreAuthorize("hasRole('ADMIN')")
public class TenantAdminController {

  private final TenantService tenants;
  private final TenantQueryService queries;

  public TenantAdminController(TenantService tenants, TenantQueryService queries) {
    this.tenants = tenants;
    this.queries = queries;
  }

  /** 创建租户请求。 */
  public record CreateTenant(
      @NotBlank String tenantKey, @NotBlank String name, @NotBlank String reason) {}

  /** 更新租户名称请求。 */
  public record UpdateTenant(@NotBlank String name, @NotBlank String reason) {}

  /** 带原因的写操作请求。 */
  public record ReasonRequest(@NotBlank String reason) {}

  /** 新增成员请求。 */
  public record MemberRequest(
      @NotBlank String subject, @NotBlank String role, @NotBlank String reason) {}

  /** 调整成员粗角色请求。 */
  public record RoleRequest(@NotBlank String role, @NotBlank String reason) {}

  @GetMapping
  public List<TenantViews.TenantView> list() {
    return queries.list();
  }

  @GetMapping("/{id}")
  public TenantViews.TenantView get(@PathVariable String id) {
    return queries.get(id);
  }

  @PostMapping
  public TenantViews.TenantView create(
      @Valid @RequestBody CreateTenant request,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    return tenants.create(
        request.tenantKey(), request.name(), request.reason(), actor.subject(), actor.role());
  }

  @PatchMapping("/{id}")
  public TenantViews.TenantView update(
      @PathVariable String id,
      @Valid @RequestBody UpdateTenant request,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    return tenants.rename(id, request.name(), request.reason(), actor.subject(), actor.role());
  }

  @PostMapping("/{id}/disable")
  public TenantViews.TenantView disable(
      @PathVariable String id,
      @Valid @RequestBody ReasonRequest request,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    return tenants.changeEnabled(id, false, request.reason(), actor.subject(), actor.role());
  }

  @PostMapping("/{id}/enable")
  public TenantViews.TenantView enable(
      @PathVariable String id,
      @Valid @RequestBody ReasonRequest request,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    return tenants.changeEnabled(id, true, request.reason(), actor.subject(), actor.role());
  }

  @PostMapping("/{id}/members")
  public TenantViews.TenantView addMember(
      @PathVariable String id,
      @Valid @RequestBody MemberRequest request,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    return tenants.addMember(
        id, request.subject(), request.role(), request.reason(), actor.subject(), actor.role());
  }

  @PutMapping("/{id}/members/{subject}")
  public TenantViews.TenantView updateMemberRole(
      @PathVariable String id,
      @PathVariable String subject,
      @Valid @RequestBody RoleRequest request,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    return tenants.updateMemberRole(
        id, subject, request.role(), request.reason(), actor.subject(), actor.role());
  }

  @PostMapping("/{id}/members/{subject}/disable")
  public TenantViews.TenantView disableMember(
      @PathVariable String id,
      @PathVariable String subject,
      @Valid @RequestBody ReasonRequest request,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    return tenants.changeMemberEnabled(
        id, subject, false, request.reason(), actor.subject(), actor.role());
  }

  @PostMapping("/{id}/members/{subject}/enable")
  public TenantViews.TenantView enableMember(
      @PathVariable String id,
      @PathVariable String subject,
      @Valid @RequestBody ReasonRequest request,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    return tenants.changeMemberEnabled(
        id, subject, true, request.reason(), actor.subject(), actor.role());
  }
}
