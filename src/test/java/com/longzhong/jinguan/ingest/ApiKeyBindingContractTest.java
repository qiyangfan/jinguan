package com.longzhong.jinguan.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.longzhong.jinguan.security.JwtService;
import com.longzhong.jinguan.user.PrincipalEntity;
import com.longzhong.jinguan.user.PrincipalRepository;
import com.longzhong.jinguan.wallet.WalletService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 验证绑定 ID 为不透明字符串，允许自定义格式并拒绝空白值。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-bind;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
    })
class ApiKeyBindingContractTest {

  @Autowired MockMvc mvc;
  @Autowired IngestService ingest;
  @Autowired WalletService wallet;
  @Autowired PrincipalRepository principals;
  @Autowired JwtService jwt;

  private String admin() {
    PrincipalEntity admin = principals.save(PrincipalEntity.jit("sub-bind-admin", "bind-admin"));
    admin.syncRole("ADMIN");
    principals.save(admin);
    return jwt.issueAccess(admin.getSubject(), admin.getUsername(), admin.getRole());
  }

  @Test
  void customIdentifierBoundAndMeteredByOriginalValue() throws Exception {
    String subject = "sub-bind-custom";
    principals.save(PrincipalEntity.jit(subject, "bind-custom"));
    bindingsOrMvcBind("k-1", subject);
    wallet.credit(subject, 1_000, WalletService.REASON_MANUAL_TOPUP, "bind-seed", null, null);
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
    assertEquals(1, result.accepted(), "k-1 计量命中绑定归账");
    assertEquals(900, wallet.balance(subject), "自定义标识扣款 100 后余额 900");
  }

  private void bindingsOrMvcBind(String keyId, String subject) throws Exception {
    mvc.perform(
            post("/admin/key-bindings")
                .header("Authorization", "Bearer " + admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"keyId\":\"" + keyId + "\",\"subject\":\"" + subject + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.keyId").value(keyId));
  }

  @Test
  void mkPrefixNotBlankWholeClassRejected() throws Exception {
    principals.save(PrincipalEntity.jit("sub-bind-mk", "bind-mk"));
    MvcResult created =
        mvc.perform(
                post("/admin/key-bindings")
                    .header("Authorization", "Bearer " + admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"keyId\":\"mk-test-1\",\"subject\":\"sub-bind-mk\"}"))
            .andReturn();
    assertEquals(201, created.getResponse().getStatus(), "mk- 前缀标识应绑定成功");
  }

  @Test
  void blankIdentifierRejected() throws Exception {
    MvcResult rejected =
        mvc.perform(
                post("/admin/key-bindings")
                    .header("Authorization", "Bearer " + admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"keyId\":\"   \",\"subject\":\"sub-bind-mk\"}"))
            .andReturn();
    assertEquals(400, rejected.getResponse().getStatus(), "空白标识必须 400");
  }
}
