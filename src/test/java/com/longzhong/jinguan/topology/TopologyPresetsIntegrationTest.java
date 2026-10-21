package com.longzhong.jinguan.topology;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 验证预设目录字段、适配器范围和管理端权限。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-presets;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.security.jwt-secret=test-secret-0123456789abcdef-0123456789abcdef",
    })
class TopologyPresetsIntegrationTest {

  /** muniu 支持的适配器类型。 */
  private static final Set<String> MUNIU_SUPPORTED_ADAPTERS =
      Set.of("openai", "qwen", "doubao", "deepseek", "moonshot", "glm", "anthropic");

  private static final List<String> PRESET_FIELDS =
      List.of(
          "name",
          "display_name",
          "adapter",
          "base_url",
          "env_suggestion",
          "models",
          "website",
          "region",
          "note");

  @Autowired MockMvc mvc;

  static String adminToken;
  static String userToken;

  private String register(String username) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"%s\",\"password\":\"presets-pass-123\"}"
                            .formatted(username)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
  }

  @Test
  @Order(1)
  void first_user_becomes_admin() throws Exception {
    adminToken = register("presets-admin");
    userToken = register("presets-user");
    assertNotEquals(adminToken, userToken);
  }

  @Test
  @Order(2)
  void admin_reads_preset_envelope_with_all_fields() throws Exception {
    MvcResult result =
        mvc.perform(get("/admin/topology/presets").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.presets").isNotEmpty())
            .andReturn();
    String body = result.getResponse().getContentAsString();
    List<?> names = JsonPath.read(body, "$.presets[*].name");
    assertTrue(names.size() >= 15, "官方厂商预设应尽量多,当前少于 15 家");
    @SuppressWarnings("unchecked")
    List<String> adapters = JsonPath.read(body, "$.presets[*].adapter");
    for (String adapter : adapters) {
      assertTrue(MUNIU_SUPPORTED_ADAPTERS.contains(adapter), () -> "预设 adapter 越界:" + adapter);
    }
    // 每条预设均包含契约字段。
    int size = names.size();
    for (int i = 0; i < size; i++) {
      final int index = i;
      for (String field : PRESET_FIELDS) {
        Object value = JsonPath.read(body, "$.presets[" + index + "]." + field);
        assertTrue(value != null, () -> "presets[%d].%s 缺失".formatted(index, field));
      }
    }
  }

  @Test
  @Order(3)
  void user_forbidden_anonymous_forbidden_too() throws Exception {
    mvc.perform(get("/admin/topology/presets").header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
    // 无凭证不能读取管理端目录。
    mvc.perform(get("/admin/topology/presets")).andExpect(status().isForbidden());
  }
}
