package com.longzhong.jinguan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.longzhong.jinguan.payment.MockOrderRepository;
import com.longzhong.jinguan.payment.PaymentExpiryScheduler;
import com.longzhong.jinguan.user.PrincipalEntity;
import com.longzhong.jinguan.user.PrincipalRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 指标集成测试的共享夹具:注册管理员、造数与指标读取,避免测试类方法数超标。 */
class OpsMetricsTestSupport {

  static final String AUTHORIZATION = "Authorization";
  static final String BEARER = "Bearer ";
  static final String DEV_INGEST_TOKEN = "dev-ingest-token";

  @Autowired MockMvc mvc;
  @Autowired MeterRegistry registry;
  @Autowired PrincipalRepository users;
  @Autowired com.longzhong.jinguan.security.JwtService jwt;
  @Autowired PaymentExpiryScheduler scheduler;
  @Autowired MockOrderRepository orders;

  String adminToken;

  /** 未创建的计数序列按 0 处理。 */
  double counter(String name, String... tags) {
    var found = registry.find(name).tags(tags).counter();
    return found == null ? 0.0 : found.count();
  }

  /** 创建普通用户供账本测试使用。 */
  String seedUser(String username) {
    return users.save(PrincipalEntity.jit("sub-" + username, username)).getSubject();
  }

  String registerFirstAdmin() {
    PrincipalEntity admin = users.save(PrincipalEntity.jit("sub-ops-admin", "ops-admin"));
    admin.grantRole("ADMIN");
    users.save(admin);
    return jwt.issueAccess(admin.getSubject(), admin.getUsername(), admin.getRole());
  }

  void credit(String subject, long amount, String refId) throws Exception {
    mvc.perform(
            post("/wallet/admin/credit")
                .header(AUTHORIZATION, BEARER + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"subject\":\""
                        + subject
                        + "\""
                        + ",\"amountCredits\":"
                        + amount
                        + ",\"refId\":\""
                        + refId
                        + "\"}"))
        .andExpect(status().isCreated());
  }

  void ingest(String rowsJsonBody) throws Exception {
    mvc.perform(
            post("/ingest/usage")
                .header(AUTHORIZATION, BEARER + DEV_INGEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rows\":[" + rowsJsonBody + "]}"))
        .andExpect(status().isOk());
  }

  /** 构造与 muniu 字段名一致的用量行。 */
  static String usageRow(String id, String keyId, long costCredits, String status) {
    return """
        {"ID":"%s","RequestID":"r-%s","TenantID":"t1","APIKeyID":"%s",
         "Provider":"deepseek","ModelAlias":"deepseek-chat","UpstreamModel":"deepseek-chat",
         "Stream":true,"Status":"%s","ErrorClass":"","PromptTokens":100,
         "CompletionTokens":50,"ReasoningTokens":0,"CachedTokens":0,"Estimated":false,
         "CostCredits":%d,"Currency":"CNY","LatencyFirstTokenMs":120,
         "LatencyTotalMs":900,"CreatedAt":"2026-09-30T00:00:00Z"}"""
        .formatted(id, id, keyId, status, costCredits);
  }
}
