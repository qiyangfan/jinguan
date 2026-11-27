package com.longzhong.jinguan.tenant;

import com.longzhong.jinguan.common.ConflictException;
import com.longzhong.jinguan.common.NotFoundException;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 租户目录只读视图:写侧唯一入口仍是 TenantService。 */
@Service
public class TenantQueryService {

  private final TenantRepository tenants;
  private final TenantMemberRepository members;

  public TenantQueryService(TenantRepository tenants, TenantMemberRepository members) {
    this.tenants = tenants;
    this.members = members;
  }

  @Transactional(readOnly = true)
  public List<TenantViews.TenantView> list() {
    return tenants.findAll().stream()
        .sorted(Comparator.comparing(TenantEntity::getTenantKey))
        .map(this::viewOf)
        .toList();
  }

  @Transactional(readOnly = true)
  public TenantViews.TenantView get(String id) {
    return viewOf(requireTenantEntity(id));
  }

  /** 其他控制域绑定租户时调用:不存在 404,已停用 409。 */
  @Transactional(readOnly = true)
  public TenantViews.TenantView requireActive(String id) {
    TenantEntity tenant = requireTenantEntity(id);
    if (!tenant.isActive()) {
      throw new ConflictException("租户已停用: " + tenant.getTenantKey());
    }
    return viewOf(tenant);
  }

  @Transactional(readOnly = true)
  public TenantViews.TenantDirectorySnapshot snapshot() {
    List<TenantViews.TenantView> views = list();
    return new TenantViews.TenantDirectorySnapshot(views.size(), views);
  }

  TenantEntity requireTenantEntity(String id) {
    return tenants.findById(id).orElseThrow(() -> new NotFoundException("租户不存在: " + id));
  }

  TenantViews.TenantView viewOf(TenantEntity tenant) {
    List<TenantViews.MemberView> memberViews =
        members.findByTenantIdOrderBySubjectAsc(tenant.getId()).stream()
            .map(m -> new TenantViews.MemberView(m.getSubject(), m.getRole(), m.getStatus()))
            .toList();
    return new TenantViews.TenantView(
        tenant.getId(),
        tenant.getTenantKey(),
        tenant.getName(),
        tenant.getStatus(),
        tenant.getUpdatedAt(),
        memberViews);
  }
}
