package com.longzhong.jinguan.wallet;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 账本分录持久化端口：仅暴露追加与读取，不继承 JpaRepository。
 *
 * <p>刻意不继承 JpaRepository/CrudRepository——那些父接口自带 delete/deleteAll，等于把 UPDATE/DELETE
 * 账本的入口交给每个调用方。收窄为 Repository + 显式 save 后，违规调用在编译期即不可达。
 */
public interface LedgerEntryRepository extends Repository<LedgerEntryEntity, Long> {

  <S extends LedgerEntryEntity> S save(S entity);

  Optional<LedgerEntryEntity> findByRefId(String refId);

  Page<LedgerEntryEntity> findBySubjectOrderByIdDesc(String subject, Pageable pageable);

  @Query(
      "SELECT COALESCE(SUM(e.amountCredits), 0) FROM LedgerEntryEntity e"
          + " WHERE e.subject = :subject")
  long sumBySubject(@Param("subject") String subject);
}
