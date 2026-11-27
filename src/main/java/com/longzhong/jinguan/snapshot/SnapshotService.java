package com.longzhong.jinguan.snapshot;

import com.longzhong.jinguan.catalog.CatalogService;
import com.longzhong.jinguan.controlversion.ControlVersionService;
import com.longzhong.jinguan.entitlement.EntitlementQueryService;
import com.longzhong.jinguan.ingest.KeyRevocationQueryService;
import com.longzhong.jinguan.policy.PolicyService;
import com.longzhong.jinguan.pricing.PricingService;
import com.longzhong.jinguan.security.SnapshotSigner;
import com.longzhong.jinguan.tenant.TenantQueryService;
import com.longzhong.jinguan.topology.TopologyService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 组装按服务投影的控制快照并签名;不写库、不触发版本递增。 */
@Service
public class SnapshotService {

  private static final String KONGMING = "kongming";
  private static final String MUNIU = "muniu";

  private final ControlVersionService versions;
  private final TenantQueryService tenants;
  private final EntitlementQueryService entitlements;
  private final PolicyService policies;
  private final CatalogService catalog;
  private final PricingService pricing;
  private final TopologyService topology;
  private final KeyRevocationQueryService revocations;
  private final SnapshotSigner signer;
  private final ObjectMapper mapper;

  public SnapshotService(
      ControlVersionService versions,
      TenantQueryService tenants,
      EntitlementQueryService entitlements,
      PolicyService policies,
      CatalogService catalog,
      PricingService pricing,
      TopologyService topology,
      KeyRevocationQueryService revocations,
      SnapshotSigner signer,
      ObjectMapper mapper) {
    this.versions = versions;
    this.tenants = tenants;
    this.entitlements = entitlements;
    this.policies = policies;
    this.catalog = catalog;
    this.pricing = pricing;
    this.topology = topology;
    this.revocations = revocations;
    this.signer = signer;
    this.mapper = mapper;
  }

  /** 构建签名后的快照;etag 与签名都基于同一份序列化字节。 */
  public ControlSnapshot.Signed build(String serviceId) {
    final long version = versions.current();
    Map<String, Object> sections = new LinkedHashMap<>();
    sections.put("tenant_directory", tenants.snapshot());
    sections.put("entitlements", entitlements.snapshot());
    sections.put("policy_packages", policies.snapshotFor(serviceId));
    if (KONGMING.equals(serviceId)) {
      sections.put("market_catalog", catalog.snapshot());
    }
    if (MUNIU.equals(serviceId)) {
      sections.put("pricing", new PricingService.PricingEnvelope(pricing.list()));
      sections.put("topology", topology.current());
      sections.put("key_revocations", revocations.snapshot());
    }
    String etag = etagOf(version, serviceId, sections);
    ControlSnapshot snapshot =
        new ControlSnapshot(
            version,
            Instant.now(),
            serviceId,
            etag,
            ControlSnapshot.securitySectionsFor(serviceId),
            sections);
    String bodyJson = serialize(snapshot);
    return new ControlSnapshot.Signed(
        bodyJson, etag, signer.sign(bodyJson), signer.keyId(), version);
  }

  private String etagOf(long version, String serviceId, Map<String, Object> sections) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(
                  (version + "|" + serviceId + "|" + serialize(sections))
                      .getBytes(StandardCharsets.UTF_8));
      return "\"" + HexFormat.of().formatHex(digest, 0, 16) + "\"";
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 不可用", e);
    }
  }

  private String serialize(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JacksonException e) {
      throw new IllegalStateException("控制快照序列化失败", e);
    }
  }
}
