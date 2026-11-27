package com.longzhong.jinguan.command;

import com.longzhong.jinguan.common.NotFoundException;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 命令只读视图:控制台列表与详情,服务端 claim/ack 不走这里。 */
@Service
public class CommandQueryService {

  private final ControlCommandRepository commands;
  private final CommandViewMapper views;

  public CommandQueryService(ControlCommandRepository commands, CommandViewMapper views) {
    this.commands = commands;
    this.views = views;
  }

  @Transactional(readOnly = true)
  public List<CommandService.CommandView> list(String serviceId, String status, int limit) {
    int safeLimit = Math.min(Math.max(limit, 1), 200);
    return commands.findAllByOrderByCreatedAtDesc(Limit.of(500)).stream()
        .filter(c -> serviceId == null || serviceId.isBlank() || serviceId.equals(c.getServiceId()))
        .filter(c -> status == null || status.isBlank() || status.equalsIgnoreCase(c.getStatus()))
        .limit(safeLimit)
        .map(views::viewOf)
        .toList();
  }

  @Transactional(readOnly = true)
  public CommandService.CommandView get(String id) {
    return views.viewOf(
        commands.findById(id).orElseThrow(() -> new NotFoundException("命令不存在: " + id)));
  }
}
