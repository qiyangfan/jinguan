package com.longzhong.jinguan.catalog;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 目录条目仓储。 */
public interface CatalogEntryRepository extends JpaRepository<CatalogEntryEntity, String> {

  Optional<CatalogEntryEntity> findByEntryKey(String entryKey);

  boolean existsByEntryKey(String entryKey);
}
