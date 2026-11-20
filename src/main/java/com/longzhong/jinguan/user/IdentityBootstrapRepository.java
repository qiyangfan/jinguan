package com.longzhong.jinguan.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** 身份引导护栏持久化端口。 */
public interface IdentityBootstrapRepository extends JpaRepository<IdentityBootstrap, Short> {

  /** 以写锁读取护栏行:并发首注册在此串行化。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT b FROM IdentityBootstrap b WHERE b.id = 1")
  Optional<IdentityBootstrap> lockGuard();
}
