package com.longzhong.jinguan.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** 并发首登同 subject 必须收敛为同一 principal,不得因唯一键竞争返回 500/400。 */
@SpringBootTest
@SuppressWarnings("PMD.DoNotUseThreads") // 并发首登必须用真实线程验证唯一键竞争
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-jit-race;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
    })
class PrincipalJitConcurrencyTest {

  @Autowired PrincipalService service;
  @Autowired PrincipalRepository principals;

  @Test
  void concurrentEnsureConvergesToSinglePrincipal() throws Exception {
    String subject = "sub-jit-race";
    Callable<PrincipalEntity> call = () -> service.ensure(subject, "jit-race");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var results = pool.invokeAll(List.of(call, call), 10, TimeUnit.SECONDS);
      for (var result : results) {
        assertEquals(subject, result.get().getSubject(), "并发首登必须返回同一 subject");
      }
    }
    assertTrue(principals.findById(subject).isPresent(), "principal 必须落库");
    assertEquals(1, principals.count(), "不得产生重复 principal");
  }
}
