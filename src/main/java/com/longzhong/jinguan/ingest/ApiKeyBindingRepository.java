package com.longzhong.jinguan.ingest;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyBindingRepository extends JpaRepository<ApiKeyBindingEntity, String> {

  /** 吊销下发名单派生:欠费停服用户名下全部绑定 key。 */
  List<ApiKeyBindingEntity> findByUserIdIn(Collection<Long> userIds);
}
