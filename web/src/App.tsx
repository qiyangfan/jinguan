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

/** 将非管理员重定向到钱包页。 */
function RequireAdmin({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  return user?.role === "ADMIN" ? <>{children}</> : <Navigate to="/wallet" replace />;
}

export default function App() {
  const { user, booted, bootstrap } = useAuth();

  // 页面加载时用 refresh cookie 恢复会话。
  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);

  if (!booted) {
    return null; // 等待会话恢复，避免登录页闪烁。
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
