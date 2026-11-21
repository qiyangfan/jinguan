package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/** 验证拓扑下发契约、ETag 和管理端权限。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-topology;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.security.jwt-secret=test-secret-0123456789abcdef-0123456789abcdef",
    })
class TopologyIngressIntegrationTest extends RefreshTestSupport {

  static final String AUTHORIZATION = "Authorization";
  static final String BEARER = "Bearer ";
  static final String TOPOLOGY_PATH = "/control/v1/topology";
  static final String PROVIDER_DEEPSEEK = "deepseek";

  /** 构造有效拓扑负载。 */
  static final String VALID_PAYLOAD =
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
          "deepseek-chat":[{"provider":"deepseek","model":"deepseek-v4-flash",
            "priority":0,"weight":0}],
          "glm-4":[{"provider":"glm","model":"glm-4.7","priority":1,"weight":0}]},
        "models":[{"provider":"deepseek","model":"deepseek-v4-flash",
        "drop_params":["temperature"],"thinking_lock":true,"max_output_tokens":8192}]
      }
      """;

  /** 引用未定义供应商的拓扑负载。 */
  static final String DANGLING_PAYLOAD =
      """
      {
        "providers":[{"name":"deepseek","adapter":"deepseek","base_url":"https://api.deepseek.com/v1",
          "keys":[{"env":"DEEPSEEK_API_KEY_1","weight":1}],"weight":70,"priority":0}],
        "model_routes":{"deepseek-chat":[{"provider":"ghost",
          "model":"whatever","priority":0,"weight":0}]},
        "models":[]
      }
      """;

  @Autowired MockMvc mvc;

  String adminToken;
  String userToken;
  String etagAfterPut;
  String etagOfGrown;

  private String loginAs(String username) {
    return accessToken(username, username.contains("admin") ? "ADMIN" : "USER");
  }

  private MvcResult putTopology(String token, String payload) throws Exception {
    return mvc.perform(
            put("/admin/topology")
                .header(AUTHORIZATION, BEARER + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andReturn();
  }

  @Test
  @Order(1)
  void firstUserBecomesAdmin() throws Exception {
    adminToken = loginAs("topo-admin");
    userToken = loginAs("topo-user");
    Assertions.assertNotEquals(adminToken, userToken, "两名用户 token 不得相同");
  }

  @Test
  @Order(2)
  void emptyStateReturns200WithEmptyPayloadAndEtag() throws Exception {
    MvcResult result =
        mvc.perform(get(TOPOLOGY_PATH))
            .andExpect(status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.providers").isEmpty())
            .andExpect(MockMvcResultMatchers.jsonPath("$.model_routes").isEmpty())
            .andExpect(MockMvcResultMatchers.jsonPath("$.models").isEmpty())
            .andReturn();
    Assertions.assertNotNull(result.getResponse().getHeader(HttpHeaders.ETAG), "空拓扑同样有确定 ETag");
  }

  @Test
  @Order(3)
  void ingressIsAnonymousButAdminPutRequiresRole() throws Exception {
    // 下发端点允许匿名读取。
    MvcResult anonymous = mvc.perform(get(TOPOLOGY_PATH)).andReturn();
    Assertions.assertEquals(200, anonymous.getResponse().getStatus(), "下发端点允许匿名读取");
    // 普通用户不能替换拓扑。
    MvcResult byUser = putTopology(userToken, VALID_PAYLOAD);
    Assertions.assertEquals(403, byUser.getResponse().getStatus(), "普通用户替换拓扑应 403");
    // 无凭证不能替换拓扑。
    MvcResult noToken =
        mvc.perform(
                put("/admin/topology")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_PAYLOAD))
            .andReturn();
    Assertions.assertEquals(403, noToken.getResponse().getStatus(), "匿名替换拓扑应 403");
  }

  @Test
  @Order(4)
  void putValidPayloadThenContractFieldsAlignMuniu() throws Exception {
    MvcResult put = putTopology(adminToken, VALID_PAYLOAD);
    Assertions.assertEquals(
        200, put.getResponse().getStatus(), "PUT 应成功: " + put.getResponse().getErrorMessage());
    etagAfterPut = put.getResponse().getHeader(HttpHeaders.ETAG);
    Assertions.assertNotNull(etagAfterPut, "PUT 响应必须带 ETag");

    MvcResult ingress =
        mvc.perform(get(TOPOLOGY_PATH))
            .andExpect(status().isOk())
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.providers[0].name").value(PROVIDER_DEEPSEEK))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.providers[0].adapter").value(PROVIDER_DEEPSEEK))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.providers[0].base_url")
                    .value("https://api.deepseek.com/v1"))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.providers[0].keys[0].env")
                    .value("DEEPSEEK_API_KEY_1"))
            // 非正 key 权重下发时归一为 1。
            .andExpect(MockMvcResultMatchers.jsonPath("$.providers[0].keys[1].weight").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.providers[0].weight").value(70))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.model_routes['deepseek-chat'][0].provider")
                    .value(PROVIDER_DEEPSEEK))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.model_routes['deepseek-chat'][0].model")
                    .value("deepseek-v4-flash"))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.models[0].provider").value(PROVIDER_DEEPSEEK))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.models[0].drop_params[0]").value("temperature"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.models[0].thinking_lock").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.models[0].max_output_tokens").value(8192))
            .andReturn();
    // PUT 与下发使用同一 ETag。
    Assertions.assertEquals(
        etagAfterPut, ingress.getResponse().getHeader(HttpHeaders.ETAG), "PUT 与下发必须使用同一 ETag");
  }

  @Test
  @Order(5)
  void conditionalRequestHits304() throws Exception {
    MvcResult notModified =
        mvc.perform(get(TOPOLOGY_PATH).header(HttpHeaders.IF_NONE_MATCH, etagAfterPut))
            .andExpect(status().isNotModified())
            .andExpect(MockMvcResultMatchers.content().string(""))
            .andReturn();
    Assertions.assertEquals(304, notModified.getResponse().getStatus(), "命中 If-None-Match 应 304");
  }

  @Test
  @Order(6)
  void replaySameContentKeepsEtag() throws Exception {
    MvcResult result = putTopology(adminToken, VALID_PAYLOAD);
    Assertions.assertEquals(200, result.getResponse().getStatus(), "相同内容重放应 200");
    Assertions.assertEquals(
        etagAfterPut,
        result.getResponse().getHeader(HttpHeaders.ETAG),
        "内容寻址 ETag:相同内容重放不变,muniu 拉取自然 304");
  }

  @Test
  @Order(7)
  void changedContentRotatesEtag() throws Exception {
    MvcResult result = putTopology(adminToken, GROWN_PAYLOAD);
    Assertions.assertEquals(200, result.getResponse().getStatus(), "变更内容 PUT 应 200");
    etagOfGrown = result.getResponse().getHeader(HttpHeaders.ETAG);
    Assertions.assertNotEquals(etagAfterPut, etagOfGrown, "内容变更 ETag 必须轮换");
    // 旧 ETag 返回完整负载。
    mvc.perform(get(TOPOLOGY_PATH).header(HttpHeaders.IF_NONE_MATCH, etagAfterPut))
        .andExpect(status().isOk());
    // 新 ETag 命中 304。
    mvc.perform(get(TOPOLOGY_PATH).header(HttpHeaders.IF_NONE_MATCH, etagOfGrown))
        .andExpect(status().isNotModified());
  }

  @Test
  @Order(8)
  void danglingProviderReferenceRejectedAndOldTopologyIntact() throws Exception {
    MvcResult rejected = putTopology(adminToken, DANGLING_PAYLOAD);
    Assertions.assertEquals(400, rejected.getResponse().getStatus(), "悬空引用必须 400");
    // 校验失败后原拓扑未变。
    mvc.perform(get(TOPOLOGY_PATH).header(HttpHeaders.IF_NONE_MATCH, etagOfGrown))
        .andExpect(status().isNotModified());
  }
}
