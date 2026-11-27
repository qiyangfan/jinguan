/* 控制台导航页：集中入口，保持移动端底部标签精简。 */

import {
  FileSearch,
  Layers,
  ListChecks,
  Network,
  ScrollText,
  ShieldCheck,
  Store,
  Users,
} from "lucide-react";
import { NavLink } from "react-router";

import { PageHeader } from "@/components/page-header";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

const ENTRIES = [
  { to: "/tenants", title: "租户与成员", description: "租户、成员与粗角色", icon: Users },
  { to: "/plans", title: "套餐与权益", description: "配额、权益键与租户绑定", icon: Layers },
  { to: "/policies", title: "共享策略包", description: "激活/回滚共享策略引用", icon: ShieldCheck },
  { to: "/catalog", title: "商店目录", description: "上架、可见性与引用可用性", icon: Store },
  {
    to: "/services",
    title: "服务与只读视图",
    description: "注册健康与服务域配置只读",
    icon: Network,
  },
  {
    to: "/commands",
    title: "运行时命令",
    description: "reindex/清缓存/取消任务",
    icon: ListChecks,
  },
  { to: "/sagas", title: "跨域流程", description: "开通租户与补偿状态", icon: FileSearch },
  { to: "/audit", title: "控制面审计", description: "写操作与敏感读留痕", icon: ScrollText },
];

export default function ControlHomePage() {
  return (
    <div className="space-y-4">
      <PageHeader
        title="控制台"
        description="共享控制读写 + 服务域配置只读；所有写操作要求原因并写审计。"
      />
      <div className="grid gap-3 sm:grid-cols-2">
        {ENTRIES.map((entry) => (
          <NavLink key={entry.to} to={entry.to} className="block min-w-0">
            <Card className="h-full transition-colors hover:bg-muted/40">
              <CardHeader className="flex flex-row items-center gap-3">
                <entry.icon className="size-5 text-primary" aria-hidden />
                <div className="min-w-0">
                  <CardTitle className="text-base">{entry.title}</CardTitle>
                  <CardDescription>{entry.description}</CardDescription>
                </div>
              </CardHeader>
            </Card>
          </NavLink>
        ))}
      </div>
    </div>
  );
}
