package com.longzhong.jinguan.ingest;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用量聚合只读视图:按来源/租户/subject/日期筛选。 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class UsageAggregateAdminController {

  private final UsageEventService usageEvents;

  public UsageAggregateAdminController(UsageEventService usageEvents) {
    this.usageEvents = usageEvents;
  }

  @GetMapping("/admin/usage-aggregates")
  public List<UsageEventService.AggregateView> aggregates(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) String tenant,
      @RequestParam(required = false) String subject) {
    LocalDate end = to == null ? LocalDate.now(java.time.ZoneOffset.UTC) : to;
    LocalDate start = from == null ? end.minusDays(30) : from;
    if (start.isAfter(end)) {
      throw new IllegalArgumentException("from 不能晚于 to");
    }
    return usageEvents.queryAggregates(start, end, source, tenant, subject);
  }
}
