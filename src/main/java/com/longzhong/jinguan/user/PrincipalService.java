package com.longzhong.jinguan.user;

import com.longzhong.jinguan.common.AccountDisabledException;
import com.longzhong.jinguan.common.NotFoundException;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 本地授权主体生命周期：首登建档、禁用校验、管理员授予角色。 */
@Service
public class PrincipalService {

  private final PrincipalRepository principals;
  private final TransactionTemplate requiresNew;

  public PrincipalService(
      PrincipalRepository principals, PlatformTransactionManager transactionManager) {
    this.principals = principals;
    this.requiresNew = new TransactionTemplate(transactionManager);
    this.requiresNew.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
  }

  /** 首登建档并按 IdP 映射初始化角色;已存在主体在登录时点同步角色缓存。 */
  public PrincipalEntity ensure(String subject, String preferredUsername, String role) {
    if (subject == null || subject.isBlank() || subject.length() > 64) {
      throw new IllegalArgumentException("subject 非法");
    }
    var existing = principals.findById(subject);
    if (existing.isPresent()) {
      return syncRoleCache(existing.get(), role);
    }
    try {
      return requiresNew.execute(
          status ->
              principals.saveAndFlush(
                  PrincipalEntity.jit(subject, uniqueUsername(subject, preferredUsername), role)));
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      // 并发首登:另一个请求已建同一 subject,重读并同步角色即为幂等结果。
      return principals.findById(subject).map(p -> syncRoleCache(p, role)).orElseThrow(() -> e);
    }
  }

  @Transactional(readOnly = true)
  public PrincipalEntity get(String subject) {
    return principals
        .findById(subject)
        .orElseThrow(() -> new NotFoundException("主体不存在: " + subject));
  }

  /** 续期与受保护请求前校验存在且未禁用。 */
  @Transactional(readOnly = true)
  public void assertEnabled(String subject) {
    PrincipalEntity principal =
        principals.findById(subject).orElseThrow(AccountDisabledException::new);
    if (principal.isDisabled()) {
      throw new AccountDisabledException();
    }
  }

  private PrincipalEntity syncRoleCache(PrincipalEntity principal, String role) {
    if (role.equals(principal.getRole())) {
      return principal;
    }
    principal.syncRole(role);
    return principals.save(principal);
  }

  @Transactional
  public PrincipalEntity updateDisabled(String subject, boolean disabled) {
    PrincipalEntity principal = get(subject);
    if (disabled) {
      principal.disable();
    } else {
      principal.enable();
    }
    return principals.save(principal);
  }

  private String uniqueUsername(String subject, String preferred) {
    String base = preferred == null ? "" : preferred.trim();
    if (base.isEmpty() || base.length() > 40) {
      base = "user-" + subject.substring(0, Math.min(8, subject.length()));
    }
    base = base.toLowerCase(Locale.ROOT);
    if (!principals.existsByUsername(base)) {
      return base;
    }
    return base + "-" + subject.substring(0, Math.min(8, subject.length()));
  }
}
