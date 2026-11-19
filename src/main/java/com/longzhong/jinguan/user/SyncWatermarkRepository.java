package com.longzhong.jinguan.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 同步水位持久化端口。 */
public interface SyncWatermarkRepository extends JpaRepository<SyncWatermarkEntity, String> {

  @Query("SELECT w.appliedVersion FROM SyncWatermarkEntity w WHERE w.name = :name")
  Optional<Long> versionOf(@Param("name") String name);

  /** 原子自增(行锁串行化):并发 bump 严格递增,无丢失。 */
  @Modifying
  @Query(
      "UPDATE SyncWatermarkEntity w SET w.appliedVersion = w.appliedVersion + 1"
          + " WHERE w.name = :name")
  int bump(@Param("name") String name);
}
