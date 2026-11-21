package com.longzhong.jinguan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 未配置时 break-glass 不存在。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-breakglass-off;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
    })
class BreakGlassDisabledTest {

  @Autowired MockMvc mvc;

  @Test
  void disabledReturns404() throws Exception {
    assertEquals(
        404,
        mvc.perform(post("/auth/break-glass").header("X-Break-Glass-Token", "anything"))
            .andReturn()
            .getResponse()
            .getStatus(),
        "未配置时必须 404");
  }
}
