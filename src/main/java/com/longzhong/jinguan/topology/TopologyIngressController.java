package com.longzhong.jinguan.topology;

import com.longzhong.jinguan.common.CommittedMetrics;
import com.longzhong.jinguan.common.MetricNames;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 下发契约端点(入站适配器):muniu 数据面按 topology_url 轮询拉取(无认证 GET,依赖部署网络 隔离——见 SecurityConfig 注记),负载与 ETag 形态对齐
 * muniu FetchTopology: ETag 双引号内容哈希,If-None-Match 命中返回 304 空体。
 */
@RestController
public class TopologyIngressController {

  private final TopologyService topology;
  private final CommittedMetrics metrics;

  public TopologyIngressController(TopologyService topology, CommittedMetrics metrics) {
    this.topology = topology;
    this.metrics = metrics;
  }

  @GetMapping("/control/v1/topology")
  public ResponseEntity<TopologyService.TopologyPayload> topology(
      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
    // body 与 ETag 派生自同一次组装:并发 PUT 之下二者永不错配
    TopologyService.TopologyPayload payload = topology.current();
    String etag = topology.etagOf(payload);
    // 拉取活性与 ETag 命中率计数点在端点层:管理页读同一 service 不得计入
    boolean notModified = ifNoneMatch != null && ifNoneMatch.equals(etag);
    metrics.count(
        MetricNames.CONTROL_PULL,
        MetricNames.TAG_PIPE,
        MetricNames.PIPE_TOPOLOGY,
        MetricNames.TAG_OUTCOME,
        notModified ? MetricNames.OUTCOME_NOT_MODIFIED : MetricNames.OUTCOME_FULL);
    if (notModified) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).header(HttpHeaders.ETAG, etag).build();
    }
    return ResponseEntity.ok().header(HttpHeaders.ETAG, etag).body(payload);
  }
}
