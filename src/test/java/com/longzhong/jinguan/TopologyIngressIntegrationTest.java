package com.longzhong.jinguan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 验证拓扑下发契约、ETag、输入校验和管理端权限。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-topology;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.security.jwt-secret=test-secret-0123456789abcdef-0123456789abcdef",
    })
class TopologyIngressIntegrationTest {

  @Autowired MockMvc mvc;

  static String adminToken;
  static String userToken;
  static String etagAfterPut;
  static String etagOfGrown;

  /** 构造有效拓扑负载。 */
  static final String VALID_PAYLOAD =
      """
      {
        "providers":[{"name":"deepseek","adapter":"deepseek","base_url":"https://api.deepseek.com/v1",
          "keys":[{"env":"DEEPSEEK_API_KEY_1","weight":30},{"env":"DEEPSEEK_API_KEY_2","weight":0}],
          "weight":70,"priority":0}],
        "model_routes":{"deepseek-chat":[{"provider":"deepseek","model":"deepseek-v4-flash","priority":0,"weight":0}]},
        "models":[{"provider":"deepseek","model":"deepseek-v4-flash","drop_params":["temperature"],"thinking_lock":true,"max_output_tokens":8192}]
      }
      """;

  /** 增加供应商和路由，用于验证内容变化会更新 ETag。 */
  static final String GROWN_PAYLOAD =
      """
      {
        "providers":[{"name":"deepseek","adapter":"deepseek","base_url":"https://api.deepseek.com/v1",
          "keys":[{"env":"DEEPSEEK_API_KEY_1","weight":30},{"env":"DEEPSEEK_API_KEY_2","weight":0}],
          "weight":70,"priority":0},
         {"name":"glm","adapter":"openai","base_url":"https://open.bigmodel.cn/api/paas/v4",
          "keys":[{"env":"GLM_API_KEY_1","weight":1}],"weight":30,"priority":1}],
        "model_routes":{
          "deepseek-chat":[{"provider":"deepseek","model":"deepseek-v4-flash","priority":0,"weight":0}],
          "glm-4":[{"provider":"glm","model":"glm-4.7","priority":1,"weight":0}]},
        "models":[{"provider":"deepseek","model":"deepseek-v4-flash","drop_params":["temperature"],"thinking_lock":true,"max_output_tokens":8192}]
      }
      """;

  /** 引用未定义供应商的拓扑负载。 */
  static final String DANGLING_PAYLOAD =
      """
      {
        "providers":[{"name":"deepseek","adapter":"deepseek","base_url":"https://api.deepseek.com/v1",
          "keys":[{"env":"DEEPSEEK_API_KEY_1","weight":1}],"weight":70,"priority":0}],
        "model_routes":{"deepseek-chat":[{"provider":"ghost","model":"whatever","priority":0,"weight":0}]},
        "models":[]
      }
      """;

  private String register(String username) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"%s\",\"password\":\"topology-pass-123\"}"
                            .formatted(username)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
  }

  private MvcResult putTopology(String token, String payload) throws Exception {
    return mvc.perform(
            put("/admin/topology")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andReturn();
  }

  @Test
  @Order(1)
  void first_user_becomes_admin() throws Exception {
    adminToken = register("topo-admin");
    userToken = register("topo-user");
    assertNotEquals(adminToken, userToken);
  }

  @Test
  @Order(2)
  void empty_state_returns_200_with_empty_payload_and_etag() throws Exception {
    MvcResult result =
        mvc.perform(get("/control/v1/topology"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providers").isEmpty())
            .andExpect(jsonPath("$.model_routes").isEmpty())
            .andExpect(jsonPath("$.models").isEmpty())
            .andReturn();
    assertNotNull(result.getResponse().getHeader(HttpHeaders.ETAG), "空拓扑同样有确定 ETag");
  }

  @Test
  @Order(3)
  void ingress_is_anonymous_but_admin_put_requires_role() throws Exception {
    // 下发端点允许匿名读取。
    mvc.perform(get("/control/v1/topology")).andExpect(status().isOk());
    // 普通用户不能替换拓扑。
    mvc.perform(
            put("/admin/topology")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_PAYLOAD))
        .andExpect(status().isForbidden());
    // 无凭证不能替换拓扑。
    mvc.perform(
            put("/admin/topology").contentType(MediaType.APPLICATION_JSON).content(VALID_PAYLOAD))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(4)
  void put_valid_payload_then_contract_fields_align_muniu() throws Exception {
    MvcResult put = putTopology(adminToken, VALID_PAYLOAD);
    assertEquals(
        200, put.getResponse().getStatus(), "PUT 应成功: " + put.getResponse().getErrorMessage());
    etagAfterPut = put.getResponse().getHeader(HttpHeaders.ETAG);
    assertNotNull(etagAfterPut);

    MvcResult ingress =
        mvc.perform(get("/control/v1/topology"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providers[0].name").value("deepseek"))
            .andExpect(jsonPath("$.providers[0].adapter").value("deepseek"))
            .andExpect(jsonPath("$.providers[0].base_url").value("https://api.deepseek.com/v1"))
            .andExpect(jsonPath("$.providers[0].keys[0].env").value("DEEPSEEK_API_KEY_1"))
            // 非正 key 权重下发时归一为 1。
            .andExpect(jsonPath("$.providers[0].keys[1].weight").value(1))
            .andExpect(jsonPath("$.providers[0].weight").value(70))
            .andExpect(jsonPath("$.model_routes['deepseek-chat'][0].provider").value("deepseek"))
            .andExpect(
                jsonPath("$.model_routes['deepseek-chat'][0].model").value("deepseek-v4-flash"))
            .andExpect(jsonPath("$.models[0].provider").value("deepseek"))
            .andExpect(jsonPath("$.models[0].drop_params[0]").value("temperature"))
            .andExpect(jsonPath("$.models[0].thinking_lock").value(true))
            .andExpect(jsonPath("$.models[0].max_output_tokens").value(8192))
            .andReturn();
    // PUT 与下发使用同一 ETag。
    assertEquals(etagAfterPut, ingress.getResponse().getHeader(HttpHeaders.ETAG));
  }

  @Test
  @Order(5)
  void conditional_request_hits_304() throws Exception {
    mvc.perform(get("/control/v1/topology").header(HttpHeaders.IF_NONE_MATCH, etagAfterPut))
        .andExpect(status().isNotModified())
        .andExpect(content().string(""));
  }

  @Test
  @Order(6)
  void replay_same_content_keeps_etag() throws Exception {
    MvcResult result = putTopology(adminToken, VALID_PAYLOAD);
    assertEquals(200, result.getResponse().getStatus());
    assertEquals(
        etagAfterPut,
        result.getResponse().getHeader(HttpHeaders.ETAG),
        "内容寻址 ETag:相同内容重放不变,muniu 拉取自然 304");
  }

  @Test
  @Order(7)
  void changed_content_rotates_etag() throws Exception {
    MvcResult result = putTopology(adminToken, GROWN_PAYLOAD);
    assertEquals(200, result.getResponse().getStatus());
    etagOfGrown = result.getResponse().getHeader(HttpHeaders.ETAG);
    assertNotEquals(etagAfterPut, etagOfGrown, "内容变更 ETag 必须轮换");
    // 旧 ETag 返回完整负载。
    mvc.perform(get("/control/v1/topology").header(HttpHeaders.IF_NONE_MATCH, etagAfterPut))
        .andExpect(status().isOk());
    // 新 ETag 命中 304。
    mvc.perform(get("/control/v1/topology").header(HttpHeaders.IF_NONE_MATCH, etagOfGrown))
        .andExpect(status().isNotModified());
  }

  @Test
  @Order(8)
  void dangling_provider_reference_rejected_and_old_topology_intact() throws Exception {
    MvcResult rejected = putTopology(adminToken, DANGLING_PAYLOAD);
    assertEquals(400, rejected.getResponse().getStatus(), "悬空引用必须 400");
    // 校验失败后原拓扑未变。
    mvc.perform(get("/control/v1/topology").header(HttpHeaders.IF_NONE_MATCH, etagOfGrown))
        .andExpect(status().isNotModified());
  }

  @Test
  @Order(9)
  void empty_routes_rejected() throws Exception {
    mvc.perform(
            put("/admin/topology")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providers\":[],\"model_routes\":{},\"models\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(10)
  void non_positive_provider_weight_rejected() throws Exception {
    String zeroWeight = VALID_PAYLOAD.replace("\"weight\":70", "\"weight\":0");
    MvcResult rejected = putTopology(adminToken, zeroWeight);
    assertEquals(400, rejected.getResponse().getStatus(), "provider weight≤0 必须 400");
  }

  @Test
  @Order(11)
  void negative_priority_rejected_aligned_with_muniu_yaml() throws Exception {
    // priority 范围与数据面一致。
    String negativePriority = VALID_PAYLOAD.replace("\"priority\":0", "\"priority\":-1");
    MvcResult rejected = putTopology(adminToken, negativePriority);
    assertEquals(400, rejected.getResponse().getStatus(), "provider priority 负值必须 400");
  }

  @Test
  @Order(12)
  void weight_beyond_int_domain_rejected() throws Exception {
    String hugeWeight = VALID_PAYLOAD.replace("\"weight\":70", "\"weight\":2147483648");
    MvcResult rejected = putTopology(adminToken, hugeWeight);
    assertEquals(400, rejected.getResponse().getStatus(), "weight 超 int 域必须 400(数据面加权累加 int 运算)");
  }
}
