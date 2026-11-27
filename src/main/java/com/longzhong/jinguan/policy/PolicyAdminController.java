package com.longzhong.jinguan.policy;

import com.longzhong.jinguan.common.StrictJson;
import com.longzhong.jinguan.security.JwtService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 策略包激活/回滚;请求体按字段白名单解析,规则体字段一律拒绝。 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class PolicyAdminController {

  private static final Set<String> CREATE_FIELDS =
      Set.of("packageKey", "serviceId", "scope", "tenantId", "rulesetVersion", "reason");
  private static final Set<String> ACTIVATE_FIELDS = Set.of("rulesetVersion", "reason", "confirm");
  private static final Set<String> ROLLBACK_FIELDS = Set.of("reason", "confirm");

  private final PolicyService policies;
  private final ObjectMapper mapper;

  public PolicyAdminController(PolicyService policies, ObjectMapper mapper) {
    this.policies = policies;
    this.mapper = mapper;
  }

  /** 创建策略包请求。 */
  public record CreateRequest(
      @NotBlank String packageKey,
      @NotBlank String serviceId,
      @NotBlank String scope,
      String tenantId,
      String rulesetVersion,
      @NotBlank String reason) {}

  /** 激活策略包版本请求。 */
  public record ActivateRequest(
      @NotBlank String rulesetVersion, @NotBlank String reason, Boolean confirm) {}

  /** 回滚策略包请求。 */
  public record RollbackRequest(@NotBlank String reason, Boolean confirm) {}

  @GetMapping("/admin/policies")
  public List<PolicyViews.PolicyPackageView> list() {
    return policies.list();
  }

  @PostMapping("/admin/policies")
  public PolicyViews.PolicyPackageView create(
      @RequestBody JsonNode body, @AuthenticationPrincipal JwtService.LoginUser actor) {
    StrictJson.requireOnly(body, CREATE_FIELDS);
    CreateRequest request = mapper.treeToValue(body, CreateRequest.class);
    return policies.create(
        request.packageKey(),
        request.serviceId(),
        request.scope(),
        request.tenantId(),
        request.rulesetVersion(),
        request.reason(),
        actor.subject(),
        actor.role());
  }

  @PostMapping("/admin/policies/{id}/activate")
  public PolicyViews.PolicyPackageView activate(
      @PathVariable String id,
      @RequestBody JsonNode body,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    StrictJson.requireOnly(body, ACTIVATE_FIELDS);
    ActivateRequest request = mapper.treeToValue(body, ActivateRequest.class);
    return policies.activate(
        id,
        request.rulesetVersion(),
        request.reason(),
        Boolean.TRUE.equals(request.confirm()),
        actor.subject(),
        actor.role());
  }

  @PostMapping("/admin/policies/{id}/rollback")
  public PolicyViews.PolicyPackageView rollback(
      @PathVariable String id,
      @RequestBody JsonNode body,
      @AuthenticationPrincipal JwtService.LoginUser actor) {
    StrictJson.requireOnly(body, ROLLBACK_FIELDS);
    RollbackRequest request = mapper.treeToValue(body, RollbackRequest.class);
    return policies.rollback(
        id,
        request.reason(),
        Boolean.TRUE.equals(request.confirm()),
        actor.subject(),
        actor.role());
  }
}
