package com.longzhong.jinguan.entitlement;

import com.longzhong.jinguan.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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

/** 套餐/权益管理;全部写操作要求原因并审计。 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class EntitlementAdminController {

  private final EntitlementService entitlement;
  private final EntitlementQueryService queries;

  public EntitlementAdminController(
      EntitlementService entitlement, EntitlementQueryService queries) {
    this.entitlement = entitlement;
    this.queries = queries;
  }

  /** 配额输入。 */
  public record QuotaInput(@NotBlank String dimension, @NotBlank String unit, @Min(0) long limit) {}

  /** 创建套餐请求。 */
  public record PlanCreate(
      @NotBlank String planKey,
      @NotBlank String name,
      List<QuotaInput> quotas,
      List<String> entitlementKeys,
      @NotBlank String reason) {

    /** 复制列表,避免外部可变集合泄漏。 */
    public PlanCreate {
      quotas = quotas == null ? List.of() : List.copyOf(quotas);
      entitlementKeys = entitlementKeys == null ? List.of() : List.copyOf(entitlementKeys);
    }
  }

  /** 更新套餐请求。 */
  public record PlanUpdate(
      @NotBlank String name,
      List<QuotaInput> quotas,
      List<String> entitlementKeys,
      @NotBlank String reason) {

    /** 复制列表,避免外部可变集合泄漏。 */
    public PlanUpdate {
      quotas = quotas == null ? List.of() : List.copyOf(quotas);
      entitlementKeys = entitlementKeys == null ? List.of() : List.copyOf(entitlementKeys);
    }
  }

  /** 带原因的写操作请求。 */
  public record ReasonRequest(@NotBlank String reason) {}

  /** 绑定套餐请求。 */
  public record PlanBindRequest(@NotBlank String planKey, @NotBlank String reason) {}

  @GetMapping("/admin/plans")
  public List<EntitlementViews.PlanView> listPlans() {
    return queries.listPlans();
  }

  @PostMapping("/admin/plans")
  public EntitlementViews.PlanView create(
      @Valid @RequestBody PlanCreate request, @AuthenticationPrincipal JwtService.LoginUser actor) {
    return entitlement.create(
        request.planKey(),
        request.name(),
        quotaViews(request.quotas()),
        request.entitlementKeys(),
        request.reason(),
        actor.subject(),
        actor.role());
  }

  @PutMapping("/admin/plans/{id}")
  public EntitlementViews.PlanView update(
      @PathVariable String id,
      @Valid @RequestBody PlanUpdate request,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    return entitlement.update(
        id,
        request.name(),
        quotaViews(request.quotas()),
        request.entitlementKeys(),
        request.reason(),
        actor.subject(),
        actor.role());
  }

  @PostMapping("/admin/plans/{id}/delete")
  public EntitlementViews.PlanView delete(
      @PathVariable String id,
      @Valid @RequestBody ReasonRequest request,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    return entitlement.delete(id, request.reason(), actor.subject(), actor.role());
  }

  @GetMapping("/admin/tenant-bindings")
  public List<EntitlementViews.BindingView> listBindings() {
    return queries.listBindings();
  }

  @PostMapping("/admin/tenants/{tenantId}/plan")
  public EntitlementViews.BindingView bind(
      @PathVariable String tenantId,
      @Valid @RequestBody PlanBindRequest request,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    return entitlement.bind(
        tenantId, request.planKey(), request.reason(), actor.subject(), actor.role());
  }

  @PostMapping("/admin/tenants/{tenantId}/plan/remove")
  public EntitlementViews.BindingView unbind(
      @PathVariable String tenantId,
      @Valid @RequestBody ReasonRequest request,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    return entitlement.unbind(tenantId, request.reason(), actor.subject(), actor.role());
  }

  private static List<EntitlementViews.QuotaView> quotaViews(List<QuotaInput> inputs) {
    if (inputs == null) {
      return List.of();
    }
    return inputs.stream()
        .map(q -> new EntitlementViews.QuotaView(q.dimension(), q.unit(), q.limit()))
        .toList();
  }
}
