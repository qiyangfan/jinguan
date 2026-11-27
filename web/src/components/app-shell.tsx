/* 应用外壳：桌面侧栏；移动端顶栏、底部导航和账户弹层。 */

import {
  FileSearch,
  KeyRound,
  Layers,
  LayoutDashboard,
  ListChecks,
  LogOut,
  MessageSquareText,
  Moon,
  Network,
  ScrollText,
  ShieldCheck,
  Store,
  Sun,
  Tags,
  UserRound,
  Users,
  Wallet,
} from "lucide-react";
import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router";

import { BottomSheetContent } from "@/components/bottom-sheet-dialog";
import { Button } from "@/components/ui/button";
import { Sheet, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { useAuth } from "@/lib/auth-store";
import { useTheme } from "@/lib/theme-store";

const NAV_ITEMS = [
  { to: "/wallet", label: "钱包", icon: Wallet, admin: false, mobile: true },
  { to: "/payments", label: "支付", icon: MessageSquareText, admin: false, mobile: true },
  { to: "/control", label: "控制台", icon: LayoutDashboard, admin: true, mobile: true },
  { to: "/tenants", label: "租户", icon: Users, admin: true, mobile: false },
  { to: "/plans", label: "套餐权益", icon: Layers, admin: true, mobile: false },
  { to: "/policies", label: "策略包", icon: ShieldCheck, admin: true, mobile: false },
  { to: "/catalog", label: "商店目录", icon: Store, admin: true, mobile: false },
  { to: "/services", label: "服务", icon: Network, admin: true, mobile: true },
  { to: "/commands", label: "命令", icon: ListChecks, admin: true, mobile: false },
  { to: "/sagas", label: "跨域流程", icon: FileSearch, admin: true, mobile: false },
  { to: "/audit", label: "审计", icon: ScrollText, admin: true, mobile: false },
  { to: "/pricing", label: "价目管理", icon: Tags, admin: true, mobile: false },
  { to: "/topology", label: "渠道拓扑", icon: Network, admin: true, mobile: false },
  { to: "/keys", label: "密钥绑定", icon: KeyRound, admin: true, mobile: false },
];

/** 未知角色显示原值。 */
const ROLE_LABELS: Record<string, string> = {
  ADMIN: "管理员",
  USER: "普通用户",
};

export function AppShell() {
  const { user, logout } = useAuth();
  const { theme, toggle } = useTheme();
  const navigate = useNavigate();
  const [accountOpen, setAccountOpen] = useState(false);

  const navItems = NAV_ITEMS.filter((item) => !item.admin || user?.role === "ADMIN");
  const mobileItems = navItems.filter((item) => item.mobile);

  const navClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-2 rounded-md px-3 py-2 text-md whitespace-nowrap ${
      isActive ? "bg-secondary font-medium text-primary" : "text-foreground hover:bg-muted"
    }`;

  const tabClass = ({ isActive }: { isActive: boolean }) =>
    `flex min-w-16 flex-1 flex-col items-center gap-0.5 px-1 py-2 text-3xs ${
      isActive ? "font-medium text-primary" : "text-muted-foreground"
    }`;

  return (
    <div className="flex min-h-screen flex-col bg-background md:flex-row">
      {/* 移动顶栏 */}
      <header className="sticky top-0 z-30 flex items-center justify-between gap-2 border-b bg-card px-3 pt-[calc(0.5rem+env(safe-area-inset-top))] pb-2 md:hidden">
        <div className="min-w-0 text-base font-semibold text-primary">锦官 · 控制面</div>
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            className="size-11"
            onClick={toggle}
            aria-label="切换深浅主题"
          >
            {theme === "dark" ? <Sun className="size-5" /> : <Moon className="size-5" />}
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="size-11"
            onClick={() => setAccountOpen(true)}
            aria-label="账户"
          >
            <UserRound className="size-5" />
          </Button>
        </div>
      </header>

      {/* 桌面侧栏 */}
      <aside className="hidden w-56 shrink-0 flex-col border-r bg-card md:flex">
        <div className="flex items-center justify-between px-4 py-4">
          <div>
            <div className="text-lg font-semibold text-primary">锦官 · 控制面</div>
            <div className="text-3xs text-muted-foreground">木牛平台 用户 · 钱包 · 计价</div>
          </div>
          <Button variant="ghost" size="icon" onClick={toggle} aria-label="切换深浅主题">
            {theme === "dark" ? <Sun className="size-4" /> : <Moon className="size-4" />}
          </Button>
        </div>
        <nav className="flex flex-1 flex-col space-y-1 overflow-y-auto p-2">
          {navItems.map((item) => (
            <NavLink key={item.to} to={item.to} className={navClass}>
              <item.icon className="size-4" />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t p-3">
          <div className="mb-2 text-2xs text-muted-foreground">
            {user ? `${user.username} · ${ROLE_LABELS[user.role] ?? user.role}` : ""}
          </div>
          <Button
            variant="outline"
            size="sm"
            className="w-full"
            onClick={() => {
              void logout();
              void navigate("/login");
            }}
          >
            <LogOut className="mr-1 size-3.5" />
            退出登录
          </Button>
        </div>
      </aside>

      {/* pb 为底部导航留白；min-w-0 防止宽内容撑破主区域。 */}
      <main className="min-w-0 flex-1 p-4 pb-24 md:p-6">
        <Outlet />
      </main>

      {/* 移动底部导航 */}
      <nav
        aria-label="主导航"
        className="fixed inset-x-0 bottom-0 z-30 flex border-t bg-card pb-[env(safe-area-inset-bottom)] md:hidden"
      >
        {mobileItems.map((item) => (
          <NavLink key={item.to} to={item.to} className={tabClass}>
            <item.icon className="size-5" aria-hidden />
            {item.label}
          </NavLink>
        ))}
      </nav>

      {/* 移动账户弹层 */}
      <Sheet open={accountOpen} onOpenChange={setAccountOpen}>
        <BottomSheetContent className="md:max-w-sm">
          <SheetHeader>
            <SheetTitle>账户</SheetTitle>
            <SheetDescription>
              {user ? `${user.username} · ${ROLE_LABELS[user.role] ?? user.role}` : ""}
            </SheetDescription>
          </SheetHeader>
          <Button
            variant="outline"
            className="h-11 w-full"
            onClick={() => {
              setAccountOpen(false);
              void logout();
              void navigate("/login");
            }}
          >
            <LogOut className="size-4" />
            退出登录
          </Button>
        </BottomSheetContent>
      </Sheet>
    </div>
  );
}
