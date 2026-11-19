package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/** 验证欠费停服、终态吊销、重绑限制和同步水位。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-revoke;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.security.jwt-secret=test-secret-0123456789abcdef-0123456789abcdef",
    })
class RevocationLoopIntegrationTest {

  private static final String AUTHORIZATION = "Authorization";
  private static final String BEARER = "Bearer ";
  private static final String REVOCATIONS_PATH = "/control/v1/key-revocations";
  private static final String VERSION_PATH = "$.version";

  @Autowired MockMvc mvc;

  String adminToken;

  private String register(String username) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"%s\",\"password\":\"password-123\"}".formatted(username)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
  }

  private long currentVersion() throws Exception {
    MvcResult result = mvc.perform(get(REVOCATIONS_PATH)).andExpect(status().isOk()).andReturn();
    return Long.parseLong(
        JsonPath.read(result.getResponse().getContentAsString(), VERSION_PATH).toString());
  }

  @Test
  @Order(1)
  void emptyStateContractShape() throws Exception {
    adminToken = register("revoke-admin"); // 首用户 ADMIN
    register("revoke-user"); // uid=2
    // 空快照的版本为 0，名单为空。
    MvcResult empty =
        mvc.perform(get(REVOCATIONS_PATH))
            .andExpect(MockMvcResultMatchers.jsonPath("$.revoked").isEmpty())
            .andReturn();
    Assertions.assertEquals(200, empty.getResponse().getStatus(), "空快照拉取应 200");
    long version =
        Long.parseLong(
            JsonPath.read(empty.getResponse().getContentAsString(), VERSION_PATH).toString());
    Assertions.assertEquals(0, version, "空快照版本为 0");
  }

  @Test
  @Order(2)
  void insufficientIngestSuspendsAllKeysVersionBumps() throws Exception {
    mvc.perform(
        post("/admin/key-bindings")
            .header(AUTHORIZATION, BEARER + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"keyId\":\"mk-loop-1\",\"userId\":2}"));
    mvc.perform(
        post("/admin/key-bindings")
            .header(AUTHORIZATION, BEARER + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"keyId\":\"mk-loop-2\",\"userId\":2}"));
    mvc.perform(
        post("/wallet/admin/credit")
            .header(AUTHORIZATION, BEARER + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":2,\"amountCredits\":100_000,\"refId\":\"rv-seed-1\"}"));

    final long before = currentVersion();
    // 欠费用量会保留明细并设置停服状态。
    mvc.perform(
            post("/ingest/usage")
                .header(AUTHORIZATION, BEARER + "dev-ingest-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ingestRow("rv-u-1", "mk-loop-1", 500_000)))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.skippedInsufficient").value(1));

    // 名单包含该用户全部 key，版本递增一次。
    mvc.perform(get(REVOCATIONS_PATH))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath(VERSION_PATH).value(before + 1))
        .andExpect(MockMvcResultMatchers.jsonPath("$.revoked.length()").value(2))
        .andExpect(MockMvcResultMatchers.jsonPath("$.revoked[0]").value("mk-loop-1"))
        .andExpect(MockMvcResultMatchers.jsonPath("$.revoked[1]").value("mk-loop-2"));

    // 重复欠费不再推进版本。
    mvc.perform(
            post("/ingest/usage")
                .header(AUTHORIZATION, BEARER + "dev-ingest-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ingestRow("rv-u-2", "mk-loop-2", 500_000)))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.skippedInsufficient").value(1));
    Assertions.assertEquals(before + 1, currentVersion(), "幂等置位不得重复 bump 水位");
  }

  @Test
  @Order(3)
  void creditResumesSameTransaction() throws Exception {
    final long before = currentVersion();
    // 入账后恢复服务，key 从名单移除。
    mvc.perform(
            post("/wallet/admin/credit")
                .header(AUTHORIZATION, BEARER + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":2,\"amountCredits\":1000000000,\"refId\":\"rv-seed-2\"}"))
        .andExpect(status().isCreated());
    MvcResult resumed =
        mvc.perform(get(REVOCATIONS_PATH))
            .andExpect(MockMvcResultMatchers.jsonPath("$.revoked").isEmpty())
            .andReturn();
    Assertions.assertEquals(200, resumed.getResponse().getStatus(), "恢复后拉取应 200");
    long version =
        Long.parseLong(
            JsonPath.read(resumed.getResponse().getContentAsString(), VERSION_PATH).toString());
    Assertions.assertEquals(before + 1, version, "入账解除停服应推进一次水位");
  }

  @Test
  @Order(4)
  void terminalRevokeIdempotentRebindRejected() throws Exception {
    final long before = currentVersion();
    // 首次吊销推进版本。
    mvc.perform(
            post("/admin/keys/mk-loop-1/revoke")
                .header(AUTHORIZATION, BEARER + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"泄露封禁\"}"))
        .andExpect(status().isCreated())
        .andExpect(MockMvcResultMatchers.jsonPath("$.replayed").value(false));
    // 重复吊销不推进版本。
    mvc.perform(
            post("/admin/keys/mk-loop-1/revoke")
                .header(AUTHORIZATION, BEARER + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"泄露封禁\"}"))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.replayed").value(true));
    Assertions.assertEquals(before + 1, currentVersion(), "重复吊销不得重复 bump 水位");

    // 全量名单包含已吊销 key。
    mvc.perform(get(REVOCATIONS_PATH))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.revoked[0]").value("mk-loop-1"));

    // 已吊销 key 不可重绑。
    mvc.perform(
            post("/admin/key-bindings")
                .header(AUTHORIZATION, BEARER + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"keyId\":\"mk-loop-1\",\"userId\":2}"))
        .andExpect(status().isConflict());
  }

  @Test
  @Order(5)
  void concurrentRevocationsVersionStrictlyIncreases() throws Exception {
    final long before = currentVersion();
    int threads = 10;
    CountDownLatch latch = new CountDownLatch(1);

    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      final String key = "mk-conc-" + i;
      futures.add(
          CompletableFuture.runAsync(
              () -> {
                try {
                  latch.await();
                } catch (InterruptedException e) {
                  throw new IllegalStateException("并发等待被中断", e);
                }
                try {
                  mvc.perform(
                          post("/admin/keys/" + key + "/revoke")
                              .header(AUTHORIZATION, BEARER + adminToken)
                              .contentType(MediaType.APPLICATION_JSON)
                              .content("{\"reason\":\"并发吊销\"}"))
                      .andExpect(status().isCreated());
                } catch (Exception e) {
                  throw new IllegalStateException("并发吊销请求失败", e);
                }
              }));
    }
    latch.countDown();
    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

    MvcResult result =
        mvc.perform(get(REVOCATIONS_PATH))
            .andExpect(status().isOk())
            // 已有吊销加上本轮并发吊销共 11 个 key。
            .andExpect(MockMvcResultMatchers.jsonPath("$.revoked.length()").value(11))
            .andReturn();
    long version =
        Long.parseLong(
            JsonPath.read(result.getResponse().getContentAsString(), VERSION_PATH).toString());
    Assertions.assertEquals(before + threads, version, "并发吊销恰各 bump 一次,水位严格递增");
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
