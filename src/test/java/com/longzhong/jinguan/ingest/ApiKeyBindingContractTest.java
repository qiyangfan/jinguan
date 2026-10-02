package com.longzhong.jinguan.ingest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 绑定标识契约(key-lifecycle delta):绑定的是数据面计量记录 ID(APIKeyID 同值)—— 不透明字符串原值传递,支持平台自定义标识(k-1 等),不限定
 * UUID、不整体拒绝 mk- 前缀;空白输入拒绝。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-bind;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.security.jwt-secret=test-secret-0123456789abcdef-0123456789abcdef",
    })
class ApiKeyBindingContractTest {

  @Autowired MockMvc mvc;
  @Autowired IngestService ingest;
  @Autowired com.longzhong.jinguan.wallet.WalletService wallet;

  static String adminToken; // 类级共享:JUnit 每方法新实例,避免重名注册 400

  /** 首个注册用户成为 ADMIN(与生产引导一致),拿到管理面凭证。 */
  private String admin() throws Exception {
    if (adminToken == null) {
      MvcResult result =
          mvc.perform(
                  post("/auth/register")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"username\":\"bind-admin\",\"password\":\"password-123\"}"))
              .andExpect(status().isCreated())
              .andReturn();
      adminToken = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }
    return adminToken;
  }

  @Test
  void customIdentifier_boundAndMetered_byOriginalValue() {
    // 平台自定义非 UUID 标识:绑定后计量以同一标识命中归账(绑定值 = APIKeyID,原值传递)
    bindingsOrMvcBind("k-1", 1);
    wallet.credit(
        1L,
        1_000,
        com.longzhong.jinguan.wallet.WalletService.REASON_MANUAL_TOPUP,
        "bind-seed",
        null,
        null);
    var result =
        ingest.accept(
            List.of(
                new IngestUsageRow(
                    "bind-1",
                    "rq",
                    "t1",
                    "k-1",
                    "deepseek",
                    "m",
                    null,
                    false,
                    "ok",
                    null,
                    1,
                    1,
                    0,
                    0,
                    false,
                    100,
                    "CNY",
                    null,
                    1L,
                    "2026-10-02T00:00:00Z")));
    org.junit.jupiter.api.Assertions.assertEquals(1, result.accepted(), "k-1 计量命中绑定归账");
    org.junit.jupiter.api.Assertions.assertEquals(900, wallet.balance(1L));
  }

  private void bindingsOrMvcBind(String keyId, long userId) {
    try {
      mvc.perform(
              post("/admin/key-bindings")
                  .header("Authorization", "Bearer " + admin())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"keyId\":\"" + keyId + "\",\"userId\":" + userId + "}"))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.keyId").value(keyId));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void mkPrefix_notBlank_wholeClassRejected() throws Exception {
    // 既有测试语境的 mk- 前缀标识:合法非空字符串,不因前缀被拒(值域纪律)
    mvc.perform(
            post("/admin/key-bindings")
                .header("Authorization", "Bearer " + admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"keyId\":\"mk-test-1\",\"userId\":1}"))
        .andExpect(status().isCreated());
  }

  @Test
  void blankIdentifier_rejected() throws Exception {
    mvc.perform(
            post("/admin/key-bindings")
                .header("Authorization", "Bearer " + admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"keyId\":\"   \",\"userId\":1}"))
        .andExpect(status().isBadRequest());
  }
}
