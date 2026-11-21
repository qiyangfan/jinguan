package com.longzhong.jinguan.user;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** 过期 refresh 会话清理：只删过期行，不影响仍有效的票据。 */
@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-refresh-cleanup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
    })
class RefreshSessionCleanupTest {

  @Autowired RefreshSessionRepository sessions;
  @Autowired PrincipalRepository principals;
  @Autowired RefreshSessionCleanupScheduler cleanup;
  @PersistenceContext EntityManager em;

  @Test
  @Transactional // 预热 consume 与清理共用测试事务;断言前清一级缓存读真实库
  void expiredSessionsPurgedAndFreshKept() {
    String subject = "sub-refresh-cleanup";
    if (principals.findById(subject).isEmpty()) {
      principals.save(PrincipalEntity.jit(subject, "清理测试"));
    }
    sessions.save(RefreshSession.create("jti-expired", subject, Instant.now().minusSeconds(1)));
    sessions.save(RefreshSession.create("jti-fresh", subject, Instant.now().plusSeconds(600)));
    sessions.save(RefreshSession.create("jti-consumed", subject, Instant.now().plusSeconds(600)));
    Assertions.assertEquals(
        1, sessions.consume("jti-consumed", Instant.now()), "预热：已消费但未过期的票必须保留,否则重放检测失效");

    cleanup.purgeExpiredSessions();
    em.clear(); // 批量 delete 不经持久化上下文,清缓存后再断言

    Assertions.assertTrue(sessions.findById("jti-expired").isEmpty(), "过期 refresh 会话必须删除");
    Assertions.assertTrue(sessions.findById("jti-fresh").isPresent(), "有效 refresh 会话不得误删");
    Assertions.assertTrue(
        sessions.findById("jti-consumed").isPresent(), "已消费但未过期的票必须保留:重放检测依赖它吊销家族");
  }
}
