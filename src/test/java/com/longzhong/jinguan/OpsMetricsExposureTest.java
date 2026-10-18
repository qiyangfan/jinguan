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
 * 指标暴露面白名单回归。
 *
 * <p>判据落在 Boot 自己的 {@link PathMappedEndpoints}(已暴露 web 端点的权威清单),不用测试内 HTTP
 * 状态码:{@code @SpringBootTest} 环境下 actuator 路径会被 {@code SpaWebConfig} 的 {@code /**}
 * 资源回退遮蔽(未命中端点映射时返回 200 + index.html),而真机 {@code spring-boot:run} 不受影响 ——{@code
 * /actuator/prometheus} 实测 200 text/plain、{@code /actuator/env} 403(实录见 HANDOFF)。 拿测试内 HTTP
 * 响应断言暴露面会得出与生产相反的结论,故此处只断言「哪些端点被登记为可暴露」。
 */
@SpringBootTest
@AutoConfigureMetrics // Boot 3.4 测试环境默认关闭指标导出;不显式打开则暴露面判据在测试里恒假
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

    // 白名单反向证据:能读出配置项名/环境变量引用名/bean 拓扑的端点一律不在暴露集合内
    String[] leaky = {
      "env", "configprops", "beans", "mappings", "heapdump", "loggers", "threaddump"
    };
    for (String id : leaky) {
      assertFalse(rootPaths.contains(id), "端点 " + id + " 不得暴露");
    }
  }

  @Test
  void 积压Gauge在装配期注册且既有指标可解析() {
    assertNotNull(
        registry.find(MetricNames.PAYMENT_EXPIRY_BACKLOG).gauge(),
        "backlog 必须注册为 Gauge(兑现扫描器类注释声称的暴露面)");
    assertNotNull(registry.find(MetricNames.PAYMENT_EXPIRY_SCAN).timer(), "既有扫描耗时指标必须可解析");
  }
}
