package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 验证管理端拓扑输入的校验边界(与 TopologyIngressIntegrationTest 共享契约语义)。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-topology-validation;MODE=PostgreSQL;"
          + "DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.security.jwt-secret=test-secret-0123456789abcdef-0123456789abcdef",
    })
class TopologyValidationIntegrationTest {

  private static final String AUTHORIZATION = "Authorization";
  private static final String BEARER = "Bearer ";

  private static final String VALID_PAYLOAD =
      """
      {
        "providers":[{"name":"deepseek","adapter":"deepseek","base_url":"https://api.deepseek.com/v1",
          "keys":[{"env":"DEEPSEEK_API_KEY_1","weight":30},{"env":"DEEPSEEK_API_KEY_2","weight":0}],
          "weight":70,"priority":0}],
        "model_routes":{"deepseek-chat":[{"provider":"deepseek",
          "model":"deepseek-v4-flash","priority":0,"weight":0}]},
        "models":[{"provider":"deepseek","model":"deepseek-v4-flash",
        "drop_params":["temperature"],"thinking_lock":true,"max_output_tokens":8192}]
      }
      """;

  @Autowired MockMvc mvc;

  String adminToken;

  private MvcResult putTopology(String payload) throws Exception {
    return mvc.perform(
            put("/admin/topology")
                .header(AUTHORIZATION, BEARER + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andReturn();
  }

  @Test
  @Order(1)
  void registerAdmin() throws Exception {
    MvcResult registered =
        mvc.perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"topo-validation-admin\","
                            + "\"password\":\"topology-pass-123\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    adminToken = JsonPath.read(registered.getResponse().getContentAsString(), "$.token");
    Assertions.assertNotNull(adminToken, "首用户注册必须成功并取得 token");
  }

  @Test
  @Order(2)
  void emptyRoutesRejected() throws Exception {
    MvcResult rejected = putTopology("{\"providers\":[],\"model_routes\":{},\"models\":[]}");
    Assertions.assertEquals(400, rejected.getResponse().getStatus(), "空路由必须 400");
  }

  @Test
  @Order(3)
  void nonPositiveProviderWeightRejected() throws Exception {
    String zeroWeight = VALID_PAYLOAD.replace("\"weight\":70", "\"weight\":0");
    MvcResult rejected = putTopology(zeroWeight);
    Assertions.assertEquals(400, rejected.getResponse().getStatus(), "provider weight≤0 必须 400");
  }

  @Test
  @Order(4)
  void negativePriorityRejectedAlignedWithMuniuYaml() throws Exception {
    // priority 范围与数据面一致。
    String negativePriority =
        VALID_PAYLOAD.replace("\"weight\":70,\"priority\":0", "\"weight\":70,\"priority\":-1");
    MvcResult rejected = putTopology(negativePriority);
    Assertions.assertEquals(400, rejected.getResponse().getStatus(), "provider priority 负值必须 400");
  }

  @Test
  @Order(5)
  void weightBeyondIntDomainRejected() throws Exception {
    String hugeWeight = VALID_PAYLOAD.replace("\"weight\":70", "\"weight\":2147483648");
    MvcResult rejected = putTopology(hugeWeight);
    Assertions.assertEquals(
        400, rejected.getResponse().getStatus(), "weight 超 int 域必须 400(数据面加权累加 int 运算)");
  }
}
