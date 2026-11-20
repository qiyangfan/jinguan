package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.longzhong.jinguan.user.UserRepository;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 首注册 ADMIN:并发首注册经护栏行锁串行化,恰好一个 ADMIN,不因 count 竞态产生双管理员。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-bootstrap;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.security.rate-limit.register-per-minute=100",
    })
class FirstAdminAtomicityTest {

  @Autowired MockMvc mvc;
  @Autowired UserRepository users;

  @Test
  void concurrentFirstRegistrationsProduceSingleAdmin() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    CompletableFuture<Integer> first =
        CompletableFuture.supplyAsync(() -> register(start, "boot-a"));
    CompletableFuture<Integer> second =
        CompletableFuture.supplyAsync(() -> register(start, "boot-b"));
    start.countDown();
    Assertions.assertEquals(201, first.get(10, TimeUnit.SECONDS), "并发首注册均须成功");
    Assertions.assertEquals(201, second.get(10, TimeUnit.SECONDS), "并发首注册均须成功");
    long admins = users.findAll().stream().filter(u -> "ADMIN".equals(u.getRole())).count();
    Assertions.assertEquals(1, admins, "并发首注册只能产生一个 ADMIN");
  }

  private int register(CountDownLatch start, String username) {
    try {
      start.await();
      return mvc.perform(
              post("/auth/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"username\":\"" + username + "\",\"password\":\"password-123\"}"))
          .andReturn()
          .getResponse()
          .getStatus();
    } catch (InterruptedException e) {
      throw new IllegalStateException("并发等待被中断", e);
    } catch (Exception e) {
      throw new IllegalStateException("并发注册失败", e);
    }
  }
}
