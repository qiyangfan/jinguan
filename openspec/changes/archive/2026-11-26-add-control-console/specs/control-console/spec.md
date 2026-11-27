# Spec Delta

## Purpose

锦官控制台：共享控制面 admin UI + 管理域只读服务视图；提供租户、套餐、权益、策略包、目录、服务、命令与审计的读写，并只读展示服务域配置；不直连服务库、不代理服务域写。

## ADDED Requirements

### Requirement: 控制台页面与权限

控制台 SHALL 提供租户/成员/套餐/权益/策略包/目录/服务/命令/审计页面，并保留钱包/支付/价目/拓扑/绑定页；页面 SHALL 仅对授权管理员可见；共享控制读写 SHALL 不依赖运行时服务可用；服务域配置页 SHALL 只读展示服务只读 API/投影并标注来源，SHALL NOT 提供写入口；服务域写只在服务管理台。

#### Scenario: ADMIN 可见

- GIVEN ADMIN 登录
- WHEN 打开控制台
- THEN 看到共享控制页与服务域配置只读页

#### Scenario: 服务宕机仍可改共享控制

- GIVEN kongming 宕机
- WHEN 修改租户套餐
- THEN 保存成功，命令 pending

#### Scenario: 服务域配置只读

- GIVEN kongming 正常
- WHEN ADMIN 尝试修改数据源
- THEN 锦官无写入口；写只在 kongming 服务管理台

#### Scenario: 只读页降级

- GIVEN kongming 宕机
- WHEN 打开数据源页
- THEN 显示不可用/stale，不伪造数据

### Requirement: 控制台写操作安全

控制台写操作 SHALL 二次确认；租户/套餐/策略/目录变更 SHALL 要求原因；结果 SHALL 展示审计标识；SHALL NOT 提供任意 SQL 入口。

#### Scenario: 缺原因拒绝

- WHEN 提交套餐变更未填原因
- THEN 前端阻止，服务端 400

#### Scenario: 审计回执

- WHEN 停用租户
- THEN 显示结果与审计标识
