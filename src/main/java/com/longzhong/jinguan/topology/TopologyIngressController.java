package com.longzhong.jinguan.topology;

import com.longzhong.jinguan.common.CommittedMetrics;
import com.longzhong.jinguan.common.MetricNames;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** muniu 通过此端点拉取拓扑；支持 ETag 条件请求。 */
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
    // 响应体和 ETag 使用同一份拓扑快照。
    TopologyService.TopologyPayload payload = topology.current();
    String etag = topology.etagOf(payload);
    // 仅统计数据面拉取，不统计管理页读取。
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
