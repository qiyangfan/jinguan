package com.longzhong.jinguan.security;

import org.springframework.data.jpa.repository.JpaRepository;

/** break-glass 审计仓储。 */
public interface BreakGlassAuditRepository extends JpaRepository<BreakGlassAudit, Long> {}
