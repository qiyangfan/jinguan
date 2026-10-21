package com.longzhong.jinguan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.longzhong.jinguan.common.MetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 断言 Actuator 暴露端点白名单。
 *
 * <p>MockMvc 下未映射路径可能回落到 SPA 并返回 200，因此直接检查 {@link PathMappedEndpoints}。
 */
@SpringBootTest
@AutoConfigureMetrics // 测试上下文需显式启用指标导出。
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-metrics-exposure;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "jinguan.security.jwt-secret=test-secret-0123456789abcdef-0123456789abcdef",
    })
class OpsMetricsExposureTest {

  @Autowired PathMappedEndpoints endpoints;
  @Autowired MeterRegistry registry;

  @Test
  void 暴露面只含health与prometheus() {
    Set<String> rootPaths = new HashSet<>(endpoints.getAllRootPaths());
    assertTrue(
        rootPaths.contains("health"), "health 必须保留(镜像 HEALTHCHECK 依赖其路径与语义),实际暴露:" + rootPaths);
    assertTrue(rootPaths.contains("prometheus"), "prometheus 必须暴露,否则埋点写入即蒸发,实际暴露:" + rootPaths);

    // 配置与 Bean 信息端点不应暴露。
    String[] leaky = {
      "env", "configprops", "beans", "mappings", "heapdump", "loggers", "threaddump"
    };
    for (String id : leaky) {
      assertFalse(rootPaths.contains(id), "端点 " + id + " 不得暴露");
    }
  }

  @Test
  void 积压Gauge在装配期注册且既有指标可解析() {
    assertNotNull(registry.find(MetricNames.PAYMENT_EXPIRY_BACKLOG).gauge(), "backlog 必须注册为 Gauge");
    assertNotNull(registry.find(MetricNames.PAYMENT_EXPIRY_SCAN).timer(), "既有扫描耗时指标必须可解析");
  }
}
