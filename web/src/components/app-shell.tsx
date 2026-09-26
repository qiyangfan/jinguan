/* 应用外壳(架构组件)。
   桌面(md 及以上):左侧栏 + 内容区(原范式不变)。
   移动(md 以下):按移动端原生范式重设计,非桌面缩放——
     顶栏(品牌 + 主题 + 账户入口,sticky)+ 底部标签栏(fixed,全项直达、拇指可达,
     带 safe-area 内边距)+ 账户面板(底部弹层:身份 + 登出,窄屏此前无登出入口)。
   导航项按角色显隐(ADMIN 项对 USER 隐藏);列表/表单的移动形态见各页与
   mobile-card / bottom-sheet-dialog 单源。 */

import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router";
import {
  KeyRound,
  LogOut,
  MessageSquareText,
  Moon,
  Network,
  Sun,
  Tags,
  UserRound,
  Wallet,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { Dialog, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { SheetContent } from "@/components/bottom-sheet-dialog";
import { useAuth } from "@/lib/auth-store";
import { useTheme } from "@/lib/theme-store";

const NAV_ITEMS = [
  { to: "/wallet", label: "钱包", icon: Wallet, admin: false },
  { to: "/payments", label: "支付", icon: MessageSquareText, admin: false },
  { to: "/pricing", label: "价目管理", icon: Tags, admin: true },
  { to: "/topology", label: "渠道拓扑", icon: Network, admin: true },
  { to: "/keys", label: "密钥绑定", icon: KeyRound, admin: true },
];

/** 角色显示名(后端角色全集 USER|ADMIN,见 jinguan UserEntity;未收录值回落原文)。 */
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
      {/* ── 移动顶栏(md 以下):品牌 + 主题 + 账户 ── */}
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

      {/* ── 桌面侧栏(md 及以上) ── */}
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
        <nav className="flex flex-1 flex-col space-y-1 p-2">
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
              navigate("/login");
            }}
          >
            <LogOut className="mr-1 size-3.5" />
            退出登录
          </Button>
        </div>
      </aside>

      {/* 移动端 pb 预留底部标签栏高度(标签栏约 60px + 安全区);
          min-w-0 解除 flex item 最小内容宽度约束,防宽表格在 md 折叠点撑破 main */}
      <main className="min-w-0 flex-1 p-4 pb-24 md:p-6">
        <Outlet />
      </main>

      {/* ── 移动底部标签栏(md 以下):全项直达,active 主色高亮 ── */}
      <nav
        aria-label="主导航"
        className="fixed inset-x-0 bottom-0 z-30 flex border-t bg-card pb-[env(safe-area-inset-bottom)] md:hidden"
      >
        {navItems.map((item) => (
          <NavLink key={item.to} to={item.to} className={tabClass}>
            <item.icon className="size-5" aria-hidden />
            {item.label}
          </NavLink>
        ))}
      </nav>

      {/* ── 账户面板(移动底部弹层):身份 + 登出 ── */}
      <Dialog open={accountOpen} onOpenChange={setAccountOpen}>
        <SheetContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>账户</DialogTitle>
            <DialogDescription>
              {user ? `${user.username} · ${ROLE_LABELS[user.role] ?? user.role}` : ""}
            </DialogDescription>
          </DialogHeader>
          <Button
            variant="outline"
            className="h-11 w-full"
            onClick={() => {
              setAccountOpen(false);
              void logout();
              navigate("/login");
            }}
          >
            <LogOut className="size-4" />
            退出登录
          </Button>
        </SheetContent>
      </Dialog>
    </div>
  );
}
