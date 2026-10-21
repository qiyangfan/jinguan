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

/** 验证欠费停服、终态吊销、重绑限制和同步水位。 */
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
    // 空快照的版本为 0，名单为空。
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
    // 欠费用量会保留明细并设置停服状态。
    mvc.perform(
            post("/ingest/usage")
                .header("Authorization", "Bearer dev-ingest-token")
                .contentType("application/json")
                .content(ingestRow("rv-u-1", "mk-loop-1", 500000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.skippedInsufficient").value(1));

    // 名单包含该用户全部 key，版本递增一次。
    mvc.perform(get("/control/v1/key-revocations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(before + 1))
        .andExpect(jsonPath("$.revoked.length()").value(2))
        .andExpect(jsonPath("$.revoked[0]").value("mk-loop-1"))
        .andExpect(jsonPath("$.revoked[1]").value("mk-loop-2"));

    // 重复欠费不再推进版本。
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
    // 入账后恢复服务，key 从名单移除。
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
    // 首次吊销推进版本。
    mvc.perform(
            post("/admin/keys/mk-loop-1/revoke")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("{\"reason\":\"泄露封禁\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.replayed").value(false));
    // 重复吊销不推进版本。
    mvc.perform(
            post("/admin/keys/mk-loop-1/revoke")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("{\"reason\":\"泄露封禁\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replayed").value(true));
    assertEquals(before + 1, currentVersion(), "重复吊销不得重复 bump 水位");

    // 全量名单包含已吊销 key。
    mvc.perform(get("/control/v1/key-revocations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revoked[0]").value("mk-loop-1"));

    // 已吊销 key 不可重绑。
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
            // 已有吊销加上本轮并发吊销共 11 个 key。
            .andExpect(jsonPath("$.revoked.length()").value(11))
            .andReturn();
    long version =
        Long.parseLong(
            JsonPath.read(result.getResponse().getContentAsString(), "$.version").toString());
    assertEquals(before + threads, version, "并发吊销恰各 bump 一次,水位严格递增");
  }

  private static String ingestRow(String id, String keyId, long costCredits) {
    // muniu UsageRow 使用 PascalCase 字段名。
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
