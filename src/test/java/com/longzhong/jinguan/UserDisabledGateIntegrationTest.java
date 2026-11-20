package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.longzhong.jinguan.user.UserEntity;
import com.longzhong.jinguan.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** 账号禁用闸门:禁用拒绝登录与续期;欠费停服仍可登录(语义分离)。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-disabled;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
    })
class UserDisabledGateIntegrationTest {

  private static final String REFRESH_COOKIE = "jinguan.refresh";

  @Autowired MockMvc mvc;
  @Autowired UserRepository users;

  @Test
  void disabledUserRejectedOnLoginAndRefresh() throws Exception {
    MvcResult reg =
        mvc.perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"dis-u1\",\"password\":\"password-123\"}"))
            .andReturn();
    Assertions.assertEquals(HttpStatus.CREATED.value(), reg.getResponse().getStatus(), "注册必须成功");
    final String refresh = cookieValue(reg.getResponse().getHeader(HttpHeaders.SET_COOKIE));

    UserEntity user = users.findByUsername("dis-u1").orElseThrow();
    user.disable();
    users.save(user);

    MvcResult login =
        mvc.perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"dis-u1\",\"password\":\"password-123\"}"))
            .andReturn();
    Assertions.assertEquals(
        HttpStatus.FORBIDDEN.value(), login.getResponse().getStatus(), "禁用账号登录必须 403");

    MvcResult renewed =
        mvc.perform(post("/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, refresh))).andReturn();
    Assertions.assertEquals(
        HttpStatus.FORBIDDEN.value(), renewed.getResponse().getStatus(), "禁用账号续期必须 403");
  }

  @Test
  void suspendedUserStillCanLogin() throws Exception {
    MvcResult reg =
        mvc.perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"sus-u1\",\"password\":\"password-123\"}"))
            .andReturn();
    Assertions.assertEquals(HttpStatus.CREATED.value(), reg.getResponse().getStatus(), "注册必须成功");
    UserEntity user = users.findByUsername("sus-u1").orElseThrow();
    users.suspendIfClear(user.getId(), Instant.now());

    MvcResult login =
        mvc.perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"sus-u1\",\"password\":\"password-123\"}"))
            .andReturn();
    Assertions.assertEquals(HttpStatus.OK.value(), login.getResponse().getStatus(), "欠费停服不得阻断登录");
  }

  private static String cookieValue(String setCookie) {
    Assertions.assertNotNull(setCookie, "必须下发 refresh cookie");
    return setCookie.substring((REFRESH_COOKIE + "=").length(), setCookie.indexOf(';'));
  }
}
