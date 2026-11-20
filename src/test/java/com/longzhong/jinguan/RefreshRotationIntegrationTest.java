package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.longzhong.jinguan.security.JwtService;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** refresh 轮换:宽限内重提返回同一后继票、并发续期不互踢、登出真吊销、未落库票 fail-closed。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-refresh-rot;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      // 本类多用户注册会触发 per-IP 限流,抬高阈值以免干扰轮换断言。
      "jinguan.security.rate-limit.register-per-minute=100",
    })
class RefreshRotationIntegrationTest extends RefreshTestSupport {

  @Test
  void replayWithinGraceReturnsSameSuccessor() throws Exception {
    String r1 = register("rot-u1");
    String r2 = rotatedCookie(refresh(r1));
    // 宽限窗口内重提旧票:不得吊销,返回同一后继会话(jti 相同)。
    String replayed = rotatedCookie(refresh(r1));
    Assertions.assertEquals(jti(r2), jti(replayed), "宽限内重提旧票必须返回同一后继会话");
    Assertions.assertNotEquals(r1, replayed, "重提不得返回旧票");
    // 后继票仍可正常轮换。
    rotatedCookie(refresh(r2));
  }

  @Test
  void logoutRevokesRefreshSession() throws Exception {
    String r1 = register("rot-u2");
    MvcResult logout =
        mvc.perform(post("/auth/logout").cookie(new Cookie(REFRESH_COOKIE, r1))).andReturn();
    Assertions.assertEquals(
        HttpStatus.NO_CONTENT.value(), logout.getResponse().getStatus(), "登出必须 204");
    // 登出后旧 refresh 不可再换发。
    Assertions.assertEquals(
        HttpStatus.UNAUTHORIZED.value(), refresh(r1).getResponse().getStatus(), "登出后 refresh 必须失效");
  }

  @Test
  void normalRotationStillWorks() throws Exception {
    String r1 = register("rot-u3");
    String r2 = rotatedCookie(refresh(r1));
    Assertions.assertNotEquals(r1, r2, "轮换必须签发新 refresh");
    String r3 = rotatedCookie(refresh(r2));
    Assertions.assertNotEquals(r2, r3, "连续轮换必须逐次签发");
  }

  @Test
  void unrecordedRefreshTokenIsRejected() throws Exception {
    String r1 = register("rot-u4");
    JwtService.LoginUser info = jwtService.verifyRefresh(r1);
    // 绕过签发链路造一枚签名合法但未落库的 refresh:必须 fail-closed。
    String unrecorded = jwtService.issueRefresh(info.uid(), info.username(), info.role());
    Assertions.assertEquals(
        HttpStatus.UNAUTHORIZED.value(),
        refresh(unrecorded).getResponse().getStatus(),
        "未落库 refresh 必须拒绝");
    // 未落库票不得连坐家族:合法票仍可轮换。
    rotatedCookie(refresh(r1));
  }

  @Test
  void replayAfterLogoutWithinGraceIsRejected() throws Exception {
    String r1 = register("rot-u6");
    String r2 = rotatedCookie(refresh(r1));
    MvcResult logout =
        mvc.perform(post("/auth/logout").cookie(new Cookie(REFRESH_COOKIE, r1))).andReturn();
    Assertions.assertEquals(
        HttpStatus.NO_CONTENT.value(), logout.getResponse().getStatus(), "登出必须 204");
    // 宽限窗口不得复活已吊销会话。
    Assertions.assertEquals(
        HttpStatus.UNAUTHORIZED.value(), refresh(r1).getResponse().getStatus(), "登出后宽限内重提旧票必须 401");
    Assertions.assertEquals(
        HttpStatus.UNAUTHORIZED.value(), refresh(r2).getResponse().getStatus(), "登出后后继票必须保持吊销");
  }

  @Test
  void concurrentRefreshReturnsSingleSuccessor() throws Exception {
    String r1 = register("rot-u5");
    CountDownLatch start = new CountDownLatch(1);
    CompletableFuture<MvcResult> first =
        CompletableFuture.supplyAsync(() -> awaitAndRefresh(start, r1));
    CompletableFuture<MvcResult> second =
        CompletableFuture.supplyAsync(() -> awaitAndRefresh(start, r1));
    start.countDown();
    MvcResult a = first.get(10, TimeUnit.SECONDS);
    MvcResult b = second.get(10, TimeUnit.SECONDS);
    Assertions.assertEquals(HttpStatus.OK.value(), a.getResponse().getStatus(), "并发续期均须成功");
    Assertions.assertEquals(HttpStatus.OK.value(), b.getResponse().getStatus(), "并发续期均须成功");
    String jtiA = jti(cookieValue(a.getResponse().getHeader(HttpHeaders.SET_COOKIE)));
    String jtiB = jti(cookieValue(b.getResponse().getHeader(HttpHeaders.SET_COOKIE)));
    Assertions.assertEquals(jtiA, jtiB, "并发续期必须落到同一后继会话,不得各自签发");
  }

  private MvcResult awaitAndRefresh(CountDownLatch start, String refreshToken) {
    try {
      start.await();
      return refresh(refreshToken);
    } catch (InterruptedException e) {
      throw new IllegalStateException("并发等待被中断", e);
    } catch (Exception e) {
      throw new IllegalStateException("并发续期失败", e);
    }
  }
}
