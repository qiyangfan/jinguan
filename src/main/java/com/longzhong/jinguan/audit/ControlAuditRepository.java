package com.longzhong.jinguan.audit;

import org.springframework.data.repository.Repository;

/** 审计仓储只开放写入与读取;不声明删除/更新方法,清理走专用保留任务。 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface") // Spring Data 仓储是标记契约,不是函数式接口
public interface ControlAuditRepository extends Repository<ControlAuditEntity, String> {

  ControlAuditEntity save(ControlAuditEntity entity);
}
