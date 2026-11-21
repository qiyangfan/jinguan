package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.longzhong.jinguan.user.TokenIssuer;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** 账号禁用闸门:禁用拒绝续期;欠费停服不阻断既有 token。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-disabled;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
    })
class UserDisabledGateIntegrationTest extends RefreshTestSupport {

  @Test
  void disabledPrincipalRejectedOnRefresh() throws Exception {
    TokenIssuer.TokenPair pair = issue("dis-u1", "USER");
    var principal =
        principals.findById(jwtService.verifyAccess(pair.access()).subject()).orElseThrow();
    principal.disable();
    principals.save(principal);

    MvcResult renewed =
        mvc.perform(post("/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, pair.refresh())))
            .andReturn();
    Assertions.assertEquals(
        HttpStatus.FORBIDDEN.value(), renewed.getResponse().getStatus(), "禁用账号续期必须 403");
  }

  @Test
  void suspendedPrincipalStillHasValidAccessToken() throws Exception {
    TokenIssuer.TokenPair pair = issue("sus-u1", "USER");
    String subject = jwtService.verifyAccess(pair.access()).subject();
    principals.suspendIfClear(subject, Instant.now());

    MvcResult balance =
        mvc.perform(
                get("/wallet/balance").header(HttpHeaders.AUTHORIZATION, "Bearer " + pair.access()))
            .andReturn();
    Assertions.assertEquals(
        HttpStatus.OK.value(), balance.getResponse().getStatus(), "欠费停服不得阻断既有 token");
    Assertions.assertEquals(
        HttpStatus.OK.value(), refresh(pair.refresh()).getResponse().getStatus(), "欠费停服不得阻断续期");
  }
}
