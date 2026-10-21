package com.longzhong.jinguan.topology;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理拓扑，支持读取和整包替换。 */
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

  /** 管理页可用的只读官方预设。 */
  @GetMapping("/presets")
  public ProviderPresetCatalog.PresetEnvelope presets() {
    return ProviderPresetCatalog.envelope();
  }

  /** 替换成功后返回新 ETag。 */
  @PutMapping
  public ResponseEntity<Void> replace(@RequestBody TopologyService.TopologyPayload next) {
    String etag = topology.replaceAll(next);
    return ResponseEntity.ok().header(HttpHeaders.ETAG, etag).build();
  }
}
