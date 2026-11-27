package com.longzhong.jinguan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 控制面集成测试支撑:管理员/服务身份请求与 JSON 断言。 */
@SuppressWarnings({"PMD.TooManyMethods", "PMD.AvoidUsingHardCodedIP", "PMD.AvoidDuplicateLiterals"})
// 测试支撑按职责集中提供请求/断言小工具;回环地址为固定夹具
public abstract class ControlTestSupport extends RefreshTestSupport {

  static final String SERVICE_TOKEN = "test-service-token";
  static final String OPS_TOKEN = "ops-token";
  static final String SERVICE_ID_HEADER = "X-Service-Id";

  @Autowired ObjectMapper json;

  MvcResult adminGet(String token, String path) throws Exception {
    return mvc.perform(MockMvcRequestBuilders.get(path).header("Authorization", "Bearer " + token))
        .andReturn();
  }

  MvcResult adminPost(String token, String path, String body) throws Exception {
    return mvc.perform(
            MockMvcRequestBuilders.post(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn();
  }

  MvcResult adminPut(String token, String path, String body) throws Exception {
    return mvc.perform(
            MockMvcRequestBuilders.put(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn();
  }

  MvcResult adminPatch(String token, String path, String body) throws Exception {
    return mvc.perform(
            MockMvcRequestBuilders.patch(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn();
  }

  MvcResult servicePost(String serviceId, String path, String body) throws Exception {
    return servicePostWithToken(serviceId, path, body, SERVICE_TOKEN, "127.0.0.1");
  }

  MvcResult servicePostWithToken(
      String serviceId, String path, String body, String token, String remoteAddr)
      throws Exception {
    MockHttpServletRequestBuilder builder =
        MockMvcRequestBuilders.post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(
                request -> {
                  request.setRemoteAddr(remoteAddr);
                  return request;
                });
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    if (serviceId != null) {
      builder.header(SERVICE_ID_HEADER, serviceId);
    }
    return mvc.perform(builder).andReturn();
  }

  MvcResult serviceGet(String serviceId, String path, String etag) throws Exception {
    MockHttpServletRequestBuilder builder =
        MockMvcRequestBuilders.get(path)
            .header("Authorization", "Bearer " + SERVICE_TOKEN)
            .header(SERVICE_ID_HEADER, serviceId);
    if (etag != null) {
      builder.header(HttpHeaders.IF_NONE_MATCH, etag);
    }
    return mvc.perform(builder).andReturn();
  }

  JsonNode body(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }

  void expectStatus(int expected, MvcResult result) {
    assertEquals(
        expected,
        result.getResponse().getStatus(),
        "HTTP "
            + expected
            + " 期望,实际 "
            + result.getResponse().getStatus()
            + ": "
            + safeBody(result));
  }

  void expectJson(MvcResult result, String pointer, Object expected) throws Exception {
    JsonNode node = body(result).at(pointer);
    assertEquals(expected, node.isMissingNode() ? null : node.asString(), "JSON 指针 " + pointer);
  }

  void expectTextContains(MvcResult result, String needle) throws Exception {
    String text = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertNotNull(text, "响应体不应为空");
    if (!text.contains(needle)) {
      throw new AssertionError("响应缺少文本 " + needle + ": " + text);
    }
  }

  void expectNotContains(MvcResult result, String needle) throws Exception {
    String text = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    if (text.contains(needle)) {
      throw new AssertionError("响应不应包含 " + needle + ": " + text);
    }
  }

  void expectTrue(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  String jsonString(MvcResult result, String pointer) throws Exception {
    JsonNode node = body(result).at(pointer);
    return node.isMissingNode() || node.isNull() ? null : node.asString();
  }

  int jsonInt(MvcResult result, String pointer) throws Exception {
    return body(result).at(pointer).asInt();
  }

  private static String safeBody(MvcResult result) {
    try {
      return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    } catch (Exception e) {
      return "<unreadable>";
    }
  }
}
