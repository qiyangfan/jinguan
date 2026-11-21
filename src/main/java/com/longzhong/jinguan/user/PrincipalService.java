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

  /** 首登建立 USER 资料；绝不按 username/email 合并其他 subject。 */
  public PrincipalEntity ensure(String subject, String preferredUsername) {
    if (subject == null || subject.isBlank() || subject.length() > 64) {
      throw new IllegalArgumentException("subject 非法");
    }
    var existing = principals.findById(subject);
    if (existing.isPresent()) {
      return existing.get();
    }
    try {
      return requiresNew.execute(
          status ->
              principals.saveAndFlush(
                  PrincipalEntity.jit(subject, uniqueUsername(subject, preferredUsername))));
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      // 并发首登:另一个请求已建同一 subject,重读即为幂等结果。
      return principals.findById(subject).orElseThrow(() -> e);
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

  @Transactional
  public PrincipalEntity grantRole(String subject, String role) {
    if (!"USER".equals(role) && !"ADMIN".equals(role)) {
      throw new IllegalArgumentException("角色只允许 USER 或 ADMIN");
    }
    PrincipalEntity principal = get(subject);
    principal.grantRole(role);
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
