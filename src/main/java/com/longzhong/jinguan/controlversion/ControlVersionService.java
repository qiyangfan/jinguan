package com.longzhong.jinguan.controlversion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 控制快照版本:写操作与其版本递增同事务提交,回滚则不消耗版本。 */
@Service
public class ControlVersionService {

  static final String SNAPSHOT_SCOPE = "snapshot";

  private final ControlVersionRepository versions;

  public ControlVersionService(ControlVersionRepository versions) {
    this.versions = versions;
  }

  /** 在当前事务内递增快照版本并返回新值。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public long bump() {
    ControlVersionEntity row =
        versions
            .findWithLockById(SNAPSHOT_SCOPE)
            .orElseThrow(() -> new IllegalStateException("控制快照版本水位缺失"));
    row.increment();
    return versions.save(row).getVersion();
  }

  @Transactional(readOnly = true)
  public long current() {
    return versions
        .findById(SNAPSHOT_SCOPE)
        .orElseThrow(() -> new IllegalStateException("控制快照版本水位缺失"))
        .getVersion();
  }
}
