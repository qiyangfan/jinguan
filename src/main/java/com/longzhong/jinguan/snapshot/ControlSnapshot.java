package com.longzhong.jinguan.snapshot;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 控制快照 envelope:版本单调,ETag 内容寻址,安全类清单固定。 */
public record ControlSnapshot(
    @JsonProperty("version") long version,
    @JsonProperty("observed_at") Instant observedAt,
    @JsonProperty("service_id") String serviceId,
    @JsonProperty("etag") String etag,
    @JsonProperty("security_sections") List<String> securitySections,
    @JsonProperty("sections") Map<String, Object> sections) {

  public ControlSnapshot {
    securitySections = List.copyOf(securitySections);
    sections = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(sections));
  }

  /** 固定安全类清单(按服务投影);缺失即 fail-closed。 */
  public static List<String> securitySectionsFor(String serviceId) {
    return "muniu".equals(serviceId)
        ? List.of("tenant_directory", "entitlements", "policy_packages", "key_revocations")
        : List.of("tenant_directory", "entitlements", "policy_packages");
  }

  /** 签名后的响应体;bodyJson 与签名逐字节一致。 */
  public record Signed(
      String bodyJson, String etag, String signature, String keyId, long version) {}
}
