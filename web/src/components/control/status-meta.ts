import type { StatusMeta } from "@/components/status-badge";

/** 控制台各域状态标签;未知值回退显示原码。 */
export const TENANT_STATUS: StatusMeta = {
  ACTIVE: { label: "启用", variant: "success" },
  DISABLED: { label: "停用", variant: "destructive" },
};

export const PLAN_STATUS: StatusMeta = {
  ACTIVE: { label: "启用", variant: "success" },
  DISABLED: { label: "停用", variant: "destructive" },
};

export const POLICY_STATUS: StatusMeta = {
  ACTIVE: { label: "生效", variant: "success" },
  SUSPENDED: { label: "暂停", variant: "warning" },
};

export const COMMAND_STATUS: StatusMeta = {
  PENDING: { label: "待领取", variant: "secondary" },
  CLAIMED: { label: "执行中", variant: "outline" },
  SUCCEEDED: { label: "成功", variant: "success" },
  FAILED: { label: "失败", variant: "destructive" },
  DEAD_LETTER: { label: "死信", variant: "destructive" },
};

export const SAGA_STATUS: StatusMeta = {
  PENDING: { label: "待执行", variant: "secondary" },
  RUNNING: { label: "执行中", variant: "outline" },
  SUCCEEDED: { label: "成功", variant: "success" },
  FAILED: { label: "失败", variant: "destructive" },
  COMPENSATING: { label: "补偿中", variant: "warning" },
  COMPENSATED: { label: "已补偿", variant: "outline" },
  BLOCKED: { label: "阻塞", variant: "destructive" },
};

export const HEALTH_STATUS: StatusMeta = {
  ACTIVE: { label: "活跃", variant: "success" },
  STALE: { label: "过期", variant: "warning" },
  UNKNOWN: { label: "未知", variant: "outline" },
};

export const PROJECTION_STATE: StatusMeta = {
  FRESH: { label: "新鲜", variant: "success" },
  STALE: { label: "陈旧", variant: "warning" },
  UNKNOWN: { label: "未知", variant: "outline" },
};

export const AVAILABILITY: StatusMeta = {
  AVAILABLE: { label: "可用", variant: "success" },
  MISSING: { label: "引用失效", variant: "destructive" },
  UNKNOWN: { label: "未知", variant: "outline" },
};

export const AUDIT_RESULT: StatusMeta = {
  SUCCESS: { label: "成功", variant: "success" },
  FAILED: { label: "失败", variant: "destructive" },
  DENIED: { label: "拒绝", variant: "warning" },
};

export const ALERT_STATUS: StatusMeta = {
  PENDING: { label: "待投递", variant: "secondary" },
  DELIVERED: { label: "已投递", variant: "success" },
  FAILED: { label: "投递失败", variant: "destructive" },
  ACKED: { label: "已确认", variant: "outline" },
};
