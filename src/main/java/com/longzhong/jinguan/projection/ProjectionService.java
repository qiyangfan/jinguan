package com.longzhong.jinguan.projection;

import com.longzhong.jinguan.common.NotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 服务只读投影:经服务只读 API 拉取并缓存 last-known;服务不可用返回 STALE/UNKNOWN, 敏感键在缓存前脱敏;不直连服务库。 */
@Service
public class ProjectionService {

  private static final Logger log = LoggerFactory.getLogger(ProjectionService.class);
  private static final Pattern SENSITIVE_KEY =
      Pattern.compile(
          ".*(password|passwd|token|secret|credential|api[_-]?key|authorization|connection|dsn).*",
          Pattern.CASE_INSENSITIVE);
  private static final String MASK = "***";
  private static final String STATE_UNKNOWN = "UNKNOWN";

  private final ServiceProjectionRepository projections;
  private final ServiceReadClient readClient;
  private final ServiceReadProperties properties;
  private final ObjectMapper mapper;

  public ProjectionService(
      ServiceProjectionRepository projections,
      ServiceReadClient readClient,
      ServiceReadProperties properties,
      ObjectMapper mapper) {
    this.projections = projections;
    this.readClient = readClient;
    this.properties = properties;
    this.mapper = mapper;
  }

  /** 投影视图:state ∈ FRESH/STALE/UNKNOWN;UNKNOWN 不携带数据。 */
  public record ProjectionView(
      String serviceId,
      String state,
      boolean mappingConfigured,
      String version,
      String etag,
      Instant observedAt,
      Instant fetchedAt,
      JsonNode sections) {}

  @SuppressWarnings("PMD.DoNotUseThreads") // 线程中断标志必须恢复,否则吞掉取消语义
  @Transactional
  public ProjectionView get(String serviceId) {
    String baseUrl = properties.baseUrlOf(serviceId);
    var cached = projections.findById(serviceId);
    if (baseUrl == null) {
      return unknown(serviceId, false);
    }
    if (cached.isPresent()
        && cached
            .get()
            .getFetchedAt()
            .plusSeconds(properties.ttlSeconds())
            .isAfter(Instant.now())) {
      return viewOf(cached.get(), true);
    }
    try {
      ServiceReadClient.RawProjection raw = readClient.fetch(serviceId, baseUrl);
      String sanitized = sanitize(raw.sectionsJson()).toString();
      ServiceProjectionEntity entity =
          cached
              .map(
                  existing -> {
                    existing.refresh(raw.etag(), raw.version(), raw.observedAt(), sanitized);
                    return existing;
                  })
              .orElseGet(
                  () ->
                      ServiceProjectionEntity.of(
                          serviceId, raw.etag(), raw.version(), raw.observedAt(), sanitized));
      return viewOf(projections.save(entity), true);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return staleOrUnknown(serviceId, cached);
    } catch (IOException | RuntimeException e) {
      log.warn("服务只读投影拉取失败 service={}: {}", serviceId, e.getMessage());
      return staleOrUnknown(serviceId, cached);
    }
  }

  private ProjectionView staleOrUnknown(
      String serviceId, java.util.Optional<ServiceProjectionEntity> cached) {
    return cached.map(entity -> viewOf(entity, true)).orElseGet(() -> unknown(serviceId, true));
  }

  @Transactional
  public int purgeExpired(Instant cutoff) {
    List<ServiceProjectionEntity> expired = projections.findByFetchedAtBefore(cutoff);
    projections.deleteAll(expired);
    return expired.size();
  }

  /** 导出脱敏后的 sections JSON。 */
  public String exportJson(String serviceId) {
    ProjectionView view = get(serviceId);
    if (STATE_UNKNOWN.equals(view.state())) {
      throw new NotFoundException("服务无可用投影: " + serviceId);
    }
    return view.sections() == null ? "{}" : view.sections().toString();
  }

  private ProjectionView unknown(String serviceId, boolean mappingConfigured) {
    return new ProjectionView(
        serviceId, "UNKNOWN", mappingConfigured, null, null, null, null, null);
  }

  private ProjectionView viewOf(ServiceProjectionEntity entity, boolean mappingConfigured) {
    Instant observed = entity.getObservedAt();
    boolean fresh =
        observed != null
            && observed.plusSeconds(properties.staleAfterSeconds()).isAfter(Instant.now());
    JsonNode sections;
    try {
      sections = mapper.readTree(entity.getPayloadJson());
    } catch (RuntimeException e) {
      sections = mapper.createObjectNode();
    }
    return new ProjectionView(
        entity.getServiceId(),
        fresh ? "FRESH" : "STALE",
        mappingConfigured,
        entity.getVersion(),
        entity.getEtag(),
        observed,
        entity.getFetchedAt(),
        sections);
  }

  /** 递归替换敏感键值后再入缓存;不保留凭据/token/连接串原值。 */
  JsonNode sanitize(String sectionsJson) {
    JsonNode node;
    try {
      node = mapper.readTree(sectionsJson);
    } catch (RuntimeException e) {
      return mapper.createObjectNode();
    }
    Object converted = mapper.convertValue(node, Object.class);
    Object redacted = redactValue(converted);
    return mapper.valueToTree(redacted);
  }

  private Object redactValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach(
          (key, nested) -> {
            String name = String.valueOf(key);
            result.put(name, SENSITIVE_KEY.matcher(name).matches() ? MASK : redactValue(nested));
          });
      return result;
    }
    if (value instanceof List<?> list) {
      return list.stream().map(this::redactValue).toList();
    }
    return value;
  }

  /** 清理任务以长租约运行。 */
  static Duration cleanupLease() {
    return Duration.ofMinutes(30);
  }
}
