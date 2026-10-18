package com.longzhong.jinguan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 吊销闭环端到端(单进程,H2):欠费停服联动(ingest 余额不足 → 名单含名下 key → 充值即恢复)→ 终态吊销 → 重复吊销幂等 → 重绑 409;
 * 水位随实际状态变化严格递增(幂等不 bump),并发吊销严格收敛。契约:muniu revocationLoop 全量快照 {"version","revoked"}。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-revoke;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.security.jwt-secret=test-secret-0123456789abcdef-0123456789abcdef",
    })
class RevocationLoopIntegrationTest {

  @Autowired MockMvc mvc;

  static String adminToken;

  private String register(String username) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/auth/register")
                    .contentType("application/json")
                    .content(
                        "{\"username\":\"%s\",\"password\":\"password-123\"}".formatted(username)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
  }

  private long currentVersion() throws Exception {
    MvcResult result =
        mvc.perform(get("/control/v1/key-revocations")).andExpect(status().isOk()).andReturn();
    return Long.parseLong(
        JsonPath.read(result.getResponse().getContentAsString(), "$.version").toString());
  }

  @Test
  @Order(1)
  void emptyState_contractShape() throws Exception {
    adminToken = register("revoke-admin"); // 首用户 ADMIN
    register("revoke-user"); // uid=2
    // 空态契约:{"version":0,"revoked":[]}
    mvc.perform(get("/control/v1/key-revocations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(0))
        .andExpect(jsonPath("$.revoked").isEmpty());
  }

  @Test
  @Order(2)
  void insufficient_ingest_suspends_allKeys_versionBumps() throws Exception {
    mvc.perform(
        post("/admin/key-bindings")
            .header("Authorization", "Bearer " + adminToken)
            .contentType("application/json")
            .content("{\"keyId\":\"mk-loop-1\",\"userId\":2}"));
    mvc.perform(
        post("/admin/key-bindings")
            .header("Authorization", "Bearer " + adminToken)
            .contentType("application/json")
            .content("{\"keyId\":\"mk-loop-2\",\"userId\":2}"));
    mvc.perform(
        post("/wallet/admin/credit")
            .header("Authorization", "Bearer " + adminToken)
            .contentType("application/json")
            .content("{\"userId\":2,\"amountCredits\":100000,\"refId\":\"rv-seed-1\"}"));

    long before = currentVersion();
    // 欠费行:CostCredits 500000 > 余额 100000 → skippedInsufficient + 停服置位
    mvc.perform(
            post("/ingest/usage")
                .header("Authorization", "Bearer dev-ingest-token")
                .contentType("application/json")
                .content(ingestRow("rv-u-1", "mk-loop-1", 500000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.skippedInsufficient").value(1));

    // 名单 = 欠费用户名下全部 key;版本恰 +1
    mvc.perform(get("/control/v1/key-revocations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(before + 1))
        .andExpect(jsonPath("$.revoked.length()").value(2))
        .andExpect(jsonPath("$.revoked[0]").value("mk-loop-1"))
        .andExpect(jsonPath("$.revoked[1]").value("mk-loop-2"));

    // 重复欠费行(新行 ID 同余额不足):置位幂等,版本不再增长
    mvc.perform(
            post("/ingest/usage")
                .header("Authorization", "Bearer dev-ingest-token")
                .contentType("application/json")
                .content(ingestRow("rv-u-2", "mk-loop-2", 500000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.skippedInsufficient").value(1));
    assertEquals(before + 1, currentVersion(), "幂等置位不得重复 bump 水位");
  }

  @Test
  @Order(3)
  void credit_resumes_sameTransaction() throws Exception {
    long before = currentVersion();
    // 充值即恢复:与入账分录同事务清除停服,名下 key 退出名单
    mvc.perform(
            post("/wallet/admin/credit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("{\"userId\":2,\"amountCredits\":1000000000,\"refId\":\"rv-seed-2\"}"))
        .andExpect(status().isCreated());
    mvc.perform(get("/control/v1/key-revocations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(before + 1))
        .andExpect(jsonPath("$.revoked").isEmpty());
  }

  @Test
  @Order(4)
  void terminalRevoke_idempotent_rebindRejected() throws Exception {
    long before = currentVersion();
    // 首次吊销:落库 + 水位 +1
    mvc.perform(
            post("/admin/keys/mk-loop-1/revoke")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("{\"reason\":\"泄露封禁\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.replayed").value(false));
    // 重复吊销:幂等重放,水位不变
    mvc.perform(
            post("/admin/keys/mk-loop-1/revoke")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("{\"reason\":\"泄露封禁\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replayed").value(true));
    assertEquals(before + 1, currentVersion(), "重复吊销不得重复 bump 水位");

    // 名单含终态吊销 key
    mvc.perform(get("/control/v1/key-revocations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revoked[0]").value("mk-loop-1"));

    // 重绑守卫:409
    mvc.perform(
            post("/admin/key-bindings")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("{\"keyId\":\"mk-loop-1\",\"userId\":2}"))
        .andExpect(status().isConflict());
  }

  @Test
  @Order(5)
  void concurrent_revocations_versionStrictlyIncreases() throws Exception {
    long before = currentVersion();
    int threads = 10;
    CountDownLatch latch = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      final String key = "mk-conc-" + i;
      futures.add(
          pool.submit(
              () -> {
                latch.await();
                mvc.perform(
                        post("/admin/keys/" + key + "/revoke")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType("application/json")
                            .content("{\"reason\":\"并发吊销\"}"))
                    .andExpect(status().isCreated());
                return null;
              }));
    }
    latch.countDown();
    for (Future<?> f : futures) {
      f.get();
    }
    pool.shutdown();

    MvcResult result =
        mvc.perform(get("/control/v1/key-revocations"))
            .andExpect(status().isOk())
            // Order 4 已使 mk-loop-1 在名单(1) + 本轮并发 10 = 11
            .andExpect(jsonPath("$.revoked.length()").value(11))
            .andReturn();
    long version =
        Long.parseLong(
            JsonPath.read(result.getResponse().getContentAsString(), "$.version").toString());
    assertEquals(before + threads, version, "并发吊销恰各 bump 一次,水位严格递增");
  }

  private static String ingestRow(String id, String keyId, long costCredits) {
    // ⚠ PascalCase:muniu UsageRow 无 json tag,Go 按导出字段名序列化
    return """
        {"rows":[{"ID":"%s","RequestID":"r","TenantID":"t1","APIKeyID":"%s",
          "Provider":"deepseek","ModelAlias":"deepseek-chat","UpstreamModel":"deepseek-chat",
          "Stream":true,"Status":"ok","ErrorClass":"","PromptTokens":1,
          "CompletionTokens":1,"ReasoningTokens":0,"CachedTokens":0,"Estimated":false,
          "CostCredits":%d,"Currency":"CNY","LatencyFirstTokenMs":1,
          "LatencyTotalMs":1,"CreatedAt":"2026-09-12T00:00:00Z"}]}"""
        .formatted(id, keyId, costCredits);
  }
}
