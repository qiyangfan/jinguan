package com.longzhong.jinguan.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** JWKS 端点:匿名可读、发布 RSA 公钥、不含私钥材料。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-jwks;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
    })
class JwksEndpointIntegrationTest {

  @Autowired MockMvc mvc;

  @Test
  void jwksExposesRsaPublicKeyWithoutPrivateMaterial() throws Exception {
    MvcResult result = mvc.perform(get("/.well-known/jwks.json")).andReturn();
    Assertions.assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus(), "JWKS 必须匿名可读");
    String body = result.getResponse().getContentAsString();
    Assertions.assertTrue(body.contains("\"kty\":\"RSA\""), "必须发布 RSA 公钥");
    Assertions.assertTrue(body.contains("\"kid\""), "公钥必须带 kid 供 RP 选键");
    Assertions.assertFalse(body.contains("\"d\""), "不得暴露私钥参数 d");
  }
}
