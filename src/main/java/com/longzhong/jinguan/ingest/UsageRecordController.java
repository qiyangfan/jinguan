package com.longzhong.jinguan.ingest;

import com.longzhong.jinguan.security.JwtService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用量明细查询：本人可查自己的记录，管理员可按用户查询；与账本流水通过 refId="usage:{id}" 对应。 */
@RestController
public class UsageRecordController {

  private final UsageRecordService usageRecords;

  public UsageRecordController(UsageRecordService usageRecords) {
    this.usageRecords = usageRecords;
  }

  /** 本人用量明细(新→旧,分页)。 */
  @GetMapping("/wallet/usage")
  public List<UsageRecordEntity> mine(
      @AuthenticationPrincipal JwtService.LoginUser user,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return usageRecords.byUser(user.uid(), page, size).getContent();
  }

  /** 管理面:按用户查询用量明细。 */
  @GetMapping("/admin/usage")
  @PreAuthorize("hasRole('ADMIN')")
  public List<UsageRecordEntity> byUserId(
      @RequestParam long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Page<UsageRecordEntity> result = usageRecords.byUser(userId, page, size);
    return result.getContent();
  }
}
