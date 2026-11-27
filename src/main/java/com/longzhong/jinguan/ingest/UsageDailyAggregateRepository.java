package com.longzhong.jinguan.ingest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 用量日聚合仓储。 */
public interface UsageDailyAggregateRepository
    extends JpaRepository<UsageDailyAggregateEntity, String> {

  @SuppressWarnings("PMD.UseObjectForClearerAPI") // Spring Data 派生查询按字段命名,拆对象会破坏派生规则
  Optional<UsageDailyAggregateEntity> findBySourceAndTenantKeyAndSubjectAndMetricAndDay(
      String source, String tenantKey, String subject, String metric, LocalDate day);

  List<UsageDailyAggregateEntity> findByDayBetweenOrderByDayAscSourceAsc(
      LocalDate from, LocalDate to);
}
