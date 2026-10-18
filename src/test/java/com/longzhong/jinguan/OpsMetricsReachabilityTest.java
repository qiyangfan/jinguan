package com.longzhong.jinguan;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 暴露面判据的真容器回归（spec 硬 MUST：RANDOM_PORT）。
 *
 * <p>MockMvc 环境不含 actuator 端点映射，未注册的 actuator 路径会落到 {@code SpaWebConfig} 的 {@code /**} 资源回退并返回 200
 * + index.html——拿它验「端点是否注册」会得出与生产相反的结论，故 {@link OpsMetricsExposureTest} 把白名单判据取在 {@code
 * PathMappedEndpoints}，本类再把「prometheus 真的可达且真是 Prometheus 文本」这条正判据取在真 Servlet 容器上：这里若端点没装配，回来的会是
 * index.html 而不是 0.0.4 文本。Boot 4 起测试客户端为 RestTestClient（RestTemplate/TestRestTemplate 维护模式）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMetrics // Boot 3.4 测试环境默认关闭指标导出；不显式打开则导出面判据恒假
@AutoConfigureRestTestClient
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-metrics-port;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "jinguan.security.jwt-secret=test-secret-0123456789abcdef-0123456789abcdef",
    })
class OpsMetricsReachabilityTest {

  @Autowired RestTestClient rest;

  @Test
  void prometheus端点经真容器可达且为004文本() {
    var result =
        rest.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody(String.class)
            .returnResult();

    String contentType = String.valueOf(result.getResponseHeaders().getContentType());
    assertTrue(
        contentType.startsWith("text/plain"),
        "必须是 Prometheus 文本格式而非 SPA 回退,实际 Content-Type:" + contentType);
    assertTrue(contentType.contains("version=0.0.4"), "文本协议版本必须为 0.0.4,实际:" + contentType);

    String body = result.getResponseBody();
    assertNotNull(body, "响应体不得为空");
    assertTrue(
        body.contains("jinguan_control_pull_total"),
        "业务序列必须在场(埋点写入而导出蒸发即此断言变红),实际前 200 字:" + body.substring(0, 200));
    assertTrue(body.contains("application=\"jinguan\""), "每条序列必须带 application 标签(多实例聚合的区分维度)");
  }

  @Test
  void 健康检查与指标同端口可达() {
    rest.get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .is2xxSuccessful(); // health 路径与承载语义不得改变(镜像 HEALTHCHECK 依赖)
  }

  @Test
  void 封闭词表序列在零值时即存在() {
    String body =
        rest.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    assertNotNull(body, "响应体不得为空");
    // increase(...)==0 型告警判据对【缺失序列】返回空向量,比较不成立即恒哑:
    // 零值序列必须自启动期就在场,这是 CommittedMetrics#registerClosedSeries 存在的唯一理由
    List<String> required =
        List.of(
            "jinguan_ingest_rows_total{application=\"jinguan\",outcome=\"accepted\"}",
            "jinguan_wallet_operations_total{application=\"jinguan\",kind=\"debit\",outcome=\"rejected_conflict\"}",
            "jinguan_payment_orders_total{application=\"jinguan\",event=\"created\"}",
            "jinguan_control_pull_total{application=\"jinguan\",outcome=\"full\",pipe=\"topology\"}");
    for (String series : required) {
      assertTrue(body.contains(series), "启动期应预建零值序列,缺失:" + series);
    }
  }
}
