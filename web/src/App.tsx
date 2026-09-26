import { useEffect } from "react";
import { Navigate, Route, Routes } from "react-router";
import { AppShell } from "@/components/app-shell";
import { useAuth } from "@/lib/auth-store";
import KeysPage from "@/pages/KeysPage";
import LoginPage from "@/pages/LoginPage";
import PaymentsPage from "@/pages/PaymentsPage";
import PricingPage from "@/pages/PricingPage";
import TopologyPage from "@/pages/TopologyPage";
import WalletPage from "@/pages/WalletPage";

/** 角色守卫:ADMIN 专属页对 USER 重定向回钱包。 */
function RequireAdmin({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  return user?.role === "ADMIN" ? <>{children}</> : <Navigate to="/wallet" replace />;
}

export default function App() {
  const { user, booted, bootstrap } = useAuth();

  // 挂载静默续期:凭 refresh cookie 恢复会话(页面刷新不掉登录)
  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);

  if (!booted) {
    return null; // 续期判定中,避免登录页闪烁
  }

  if (!user) {
    return <LoginPage />;
  }

  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/wallet" element={<WalletPage />} />
        <Route path="/payments" element={<PaymentsPage />} />
        <Route
          path="/pricing"
          element={
            <RequireAdmin>
              <PricingPage />
            </RequireAdmin>
          }
        />
        <Route
          path="/topology"
          element={
            <RequireAdmin>
              <TopologyPage />
            </RequireAdmin>
          }
        />
        <Route
          path="/keys"
          element={
            <RequireAdmin>
              <KeysPage />
            </RequireAdmin>
          }
        />
        <Route path="*" element={<Navigate to="/wallet" replace />} />
      </Route>
    </Routes>
  );
}
