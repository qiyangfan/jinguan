package com.longzhong.jinguan.topology;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理面:拓扑整包运营(GET 回填编辑 / PUT 整包替换,校验拒绝见 TopologyService)。 */
@RestController
@RequestMapping("/admin/topology")
@PreAuthorize("hasRole('ADMIN')")
public class TopologyAdminController {

  private final TopologyService topology;

  public TopologyAdminController(TopologyService topology) {
    this.topology = topology;
  }

  @GetMapping
  public TopologyService.TopologyPayload current() {
    return topology.current();
  }

  /** 替换成功返回 200 + 新 ETag(与下发端点同源,客户端可立即做条件请求校验)。 */
  @PutMapping
  public ResponseEntity<Void> replace(@RequestBody TopologyService.TopologyPayload next) {
    String etag = topology.replaceAll(next);
    return ResponseEntity.ok().header(HttpHeaders.ETAG, etag).build();
  }
}
