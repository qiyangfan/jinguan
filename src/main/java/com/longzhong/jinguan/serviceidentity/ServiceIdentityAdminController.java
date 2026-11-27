package com.longzhong.jinguan.serviceidentity;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 服务身份目录只读视图;不返回任何 secret/token。 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class ServiceIdentityAdminController {

  private final ServiceAuthService serviceAuth;

  public ServiceIdentityAdminController(ServiceAuthService serviceAuth) {
    this.serviceAuth = serviceAuth;
  }

  @GetMapping("/admin/service-identities")
  public List<ServiceAuthService.DirectoryEntry> list() {
    return serviceAuth.directory();
  }
}
