/* 应用外壳(架构组件):左导航 + 右内容;窄屏(md 以下)侧栏折叠为顶部横排导航
   (多屏幕尺寸协同)。品牌区 + 主题切换(light/dark,偏好持久化)+ 登出;
   导航项按角色显隐(ADMIN 项对 USER 隐藏)。 */

import { NavLink, Outlet, useNavigate } from "react-router";
import { KeyRound, LogOut, MessageSquareText, Moon, Sun, Tags, Wallet } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useAuth } from "@/lib/auth-store";
import { useTheme } from "@/lib/theme-store";

const NAV_ITEMS = [
  { to: "/wallet", label: "钱包", icon: Wallet, admin: false },
  { to: "/payments", label: "支付", icon: MessageSquareText, admin: false },
  { to: "/pricing", label: "价目管理", icon: Tags, admin: true },
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

  const navClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-2 rounded-md px-3 py-2 text-md whitespace-nowrap ${
      isActive ? "bg-secondary font-medium text-primary" : "text-foreground hover:bg-muted"
    }`;

  return (
    <div className="flex min-h-screen flex-col md:flex-row bg-background">
      <aside className="flex shrink-0 flex-col border-r bg-card md:w-56">
        <div className="flex items-center justify-between border-b px-4 py-3 md:block md:py-5">
          <div>
            <div className="text-lg font-semibold text-primary">锦官 · 控制面</div>
            <div className="hidden text-3xs text-muted-foreground md:block">
              木牛平台 用户 · 钱包 · 计价
            </div>
          </div>
          <Button variant="ghost" size="icon" onClick={toggle} aria-label="切换深浅主题">
            {theme === "dark" ? <Sun className="size-4" /> : <Moon className="size-4" />}
          </Button>
        </div>
        <nav className="flex flex-row gap-1 overflow-x-auto p-2 md:flex-1 md:flex-col md:space-y-1">
          {NAV_ITEMS.filter((item) => !item.admin || user?.role === "ADMIN").map((item) => (
            <NavLink key={item.to} to={item.to} className={navClass}>
              <item.icon className="size-4" />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="hidden border-t p-3 md:block">
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
      <main className="flex-1 p-4 md:p-6">
        <Outlet />
      </main>
    </div>
  );
}
