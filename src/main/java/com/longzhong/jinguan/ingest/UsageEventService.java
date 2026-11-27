package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.audit.AuditActions;
import com.longzhong.jinguan.audit.AuditActor;
import com.longzhong.jinguan.audit.AuditRecorder;
import com.longzhong.jinguan.common.ForbiddenException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 多来源用量接收与聚合:幂等 (source,eventId,metric),LLM 口径只认 muniu。 */
@Service
public class UsageEventService {

  private final UsageEventRepository events;
  private final UsageDailyAggregateRepository dailyAggregates;
  private final UsageEventValidation validation;
  private final AuditRecorder recorder;

  public UsageEventService(
      UsageEventRepository events,
      UsageDailyAggregateRepository dailyAggregates,
      UsageEventValidation validation,
      AuditRecorder recorder) {
    this.events = events;
    this.dailyAggregates = dailyAggregates;
    this.validation = validation;
    this.recorder = recorder;
  }

  /** 单条事件输入。 */
  public record EventInput(
      String eventId,
      String tenantKey,
      String subject,
      String entitlementKey,
      String metric,
      long quantity,
      String unit,
      String kind,
      Instant occurredAt) {}

  /** 批量结果。 */
  public record BatchResult(int received, int accepted, int replayed) {}

  /** 聚合视图。 */
  public record AggregateView(
      String source,
      String tenantKey,
      String subject,
      String metric,
      LocalDate day,
      long quantity) {}

  /** 校验后的事件字段;tenant/subject/entitlement 可为 null。 */
  record Normalized(
      String eventId,
      String tenantKey,
      String subject,
      String entitlementKey,
      String metric,
      long quantity,
      String unit,
      String kind,
      Instant occurredAt) {}

  @Transactional
  public BatchResult accept(String source, List<EventInput> inputs) {
    int received = 0;
    int accepted = 0;
    int replayed = 0;
    for (EventInput input : inputs) {
      received++;
      Normalized normalized = validation.normalize(source, input);
      UsageEventValidation.rejectKongmingLlmMetric(source, normalized.metric());
      if (events.existsBySourceAndEventIdAndMetric(
          source, normalized.eventId(), normalized.metric())) {
        replayed++;
        continue;
      }
      events.save(
          UsageEventEntity.of(
              source,
              normalized.eventId(),
              normalized.tenantKey(),
              normalized.subject(),
              normalized.entitlementKey(),
              normalized.metric(),
              normalized.quantity(),
              normalized.unit(),
              normalized.kind(),
              normalized.occurredAt()));
      accepted++;
    }
    recorder.record(
        AuditActor.service(source),
        AuditActions.USAGE_RECEIVE,
        "usage-batch",
        null,
        null,
        null,
        Map.of("received", received, "accepted", accepted, "replayed", replayed),
        "SUCCESS");
    return new BatchResult(received, accepted, replayed);
  }

  /** 聚合未聚合事件到日聚合,并标记已聚合;可重复执行(幂等靠 aggregated_at 标记)。 */
  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // 每个聚合键仅创建一个累积器,与行数无关
  @Transactional
  public int aggregatePending() {
    List<UsageEventEntity> pending =
        events.findByAggregatedAtIsNullOrderByOccurredAtAsc(Limit.of(2000));
    if (pending.isEmpty()) {
      return 0;
    }
    Map<String, Group> groups = new LinkedHashMap<>();
    for (UsageEventEntity event : pending) {
      LocalDate day = event.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate();
      String key =
          String.join(
              "|",
              event.getSource(),
              nullToEmpty(event.getTenantKey()),
              nullToEmpty(event.getSubject()),
              event.getMetric(),
              day.toString());
      Group group = groups.get(key);
      if (group == null) {
        group = new Group(event, day);
        groups.put(key, group);
      }
      group.add(event.getQuantity());
    }
    for (Group group : groups.values()) {
      persistGroup(group);
    }
    pending.forEach(UsageEventEntity::markAggregated);
    events.saveAll(pending);
    recorder.record(
        AuditActor.system(),
        AuditActions.USAGE_AGGREGATE,
        "usage-event",
        null,
        null,
        null,
        Map.of("aggregatedEvents", pending.size(), "groups", groups.size()),
        "SUCCESS");
    return pending.size();
  }

  private void persistGroup(Group group) {
    UsageEventEntity sample = group.sample;
    var existing =
        dailyAggregates.findBySourceAndTenantKeyAndSubjectAndMetricAndDay(
            sample.getSource(),
            nullToEmpty(sample.getTenantKey()),
            nullToEmpty(sample.getSubject()),
            sample.getMetric(),
            group.day);
    if (existing.isPresent()) {
      existing.get().addQuantity(group.quantity);
      dailyAggregates.save(existing.get());
    } else {
      dailyAggregates.save(
          UsageDailyAggregateEntity.of(
              sample.getSource(),
              nullToEmpty(sample.getTenantKey()),
              nullToEmpty(sample.getSubject()),
              sample.getMetric(),
              group.day,
              group.quantity));
    }
  }

  /** 只清理已聚合事件;未聚合事件保留。 */
  @Transactional
  public int purgeAggregated(Instant cutoff) {
    List<UsageEventEntity> expired = events.findByAggregatedAtIsNotNullAndOccurredAtBefore(cutoff);
    if (expired.isEmpty()) {
      return 0;
    }
    events.deleteAll(expired);
    recorder.record(
        AuditActor.system(),
        AuditActions.USAGE_PURGE,
        "usage-event",
        null,
        null,
        "已聚合原始事件保留期清理",
        Map.of("purged", expired.size(), "cutoff", cutoff.toString()),
        "SUCCESS");
    return expired.size();
  }

  @Transactional(readOnly = true)
  public List<AggregateView> queryAggregates(
      LocalDate from, LocalDate to, String source, String tenantKey, String subject) {
    return dailyAggregates.findByDayBetweenOrderByDayAscSourceAsc(from, to).stream()
        .filter(a -> source == null || source.isBlank() || source.equals(a.getSource()))
        .filter(a -> tenantKey == null || tenantKey.isBlank() || tenantKey.equals(a.getTenantKey()))
        .filter(a -> subject == null || subject.isBlank() || subject.equals(a.getSubject()))
        .map(
            a ->
                new AggregateView(
                    a.getSource(),
                    a.getTenantKey(),
                    a.getSubject(),
                    a.getMetric(),
                    a.getDay(),
                    a.getQuantity()))
        .toList();
  }

  /** 健康通道的服务绑定校验:健康事件只能来自所属服务。 */
  public static void requireOwnService(String principalServiceId, String bodyServiceId) {
    if (bodyServiceId == null || !bodyServiceId.equals(principalServiceId)) {
      throw new ForbiddenException("服务身份与健康事件来源不一致");
    }
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static final class Group {
    private final UsageEventEntity sample;
    private final LocalDate day;
    private long quantity;

    private Group(UsageEventEntity sample, LocalDate day) {
      this.sample = sample;
      this.day = day;
    }

    private void add(long delta) {
      this.quantity += delta;
    }
  }
}
