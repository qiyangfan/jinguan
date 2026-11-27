package com.longzhong.jinguan.registry;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 服务能力/健康只读视图;不返回凭据。 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class ServiceAdminController {

  private final ServiceRegistryService registry;

  public ServiceAdminController(ServiceRegistryService registry) {
    this.registry = registry;
  }

  @GetMapping("/admin/services")
  public List<RegistryViews.ServiceView> list() {
    return registry.services();
  }
}
