package com.longzhong.jinguan.topology;

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

  public TopologyIngressController(TopologyService topology) {
    this.topology = topology;
  }

  @GetMapping("/control/v1/topology")
  public ResponseEntity<TopologyService.TopologyPayload> topology(
      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
    // body 与 ETag 派生自同一次组装:并发 PUT 之下二者永不错配
    TopologyService.TopologyPayload payload = topology.current();
    String etag = topology.etagOf(payload);
    if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).header(HttpHeaders.ETAG, etag).build();
    }
    return ResponseEntity.ok().header(HttpHeaders.ETAG, etag).body(payload);
  }
}
