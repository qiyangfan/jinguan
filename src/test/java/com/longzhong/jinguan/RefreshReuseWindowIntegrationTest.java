package com.longzhong.jinguan;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

/** 宽限窗口边界:越过 30 秒窗口的旧票重提按泄漏处理,吊销全家族。 宽限调为 1 秒以缩短用例耗时。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-refresh-window;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.security.rate-limit.register-per-minute=100",
      "jinguan.security.refresh-grace-seconds=1",
    })
class RefreshReuseWindowIntegrationTest extends RefreshTestSupport {

  @Test
  void replayAfterGraceRevokesFamily() throws Exception {
    String r1 = register("win-u1");
    String r2 = rotatedCookie(refresh(r1));
    // 宽限调为 1 秒:用带超时的 latch 越过窗口,不引入线程等待原语。
    boolean signaled = new CountDownLatch(1).await(1, TimeUnit.SECONDS);
    Assertions.assertFalse(signaled, "等待必须超时(已越过宽限窗口)");
    // 超窗重提:视为泄漏,401 并吊销全家族。
    Assertions.assertEquals(
        HttpStatus.UNAUTHORIZED.value(), refresh(r1).getResponse().getStatus(), "超窗重放旧票必须 401");
    Assertions.assertEquals(
        HttpStatus.UNAUTHORIZED.value(),
        refresh(r2).getResponse().getStatus(),
        "超窗重放必须吊销家族中已轮换的后继票");
  }
}
