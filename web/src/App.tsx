import { useEffect } from "react";
import { Navigate, Route, Routes } from "react-router";

import { AppShell } from "@/components/app-shell";
import { useAuth } from "@/lib/auth-store";
import AuditPage from "@/pages/AuditPage";
import CatalogPage from "@/pages/CatalogPage";
import CommandsPage from "@/pages/CommandsPage";
import ControlHomePage from "@/pages/ControlHomePage";
import KeysPage from "@/pages/KeysPage";
import LoginPage from "@/pages/LoginPage";
import PaymentsPage from "@/pages/PaymentsPage";
import PlansPage from "@/pages/PlansPage";
import PoliciesPage from "@/pages/PoliciesPage";
import PricingPage from "@/pages/PricingPage";
import SagasPage from "@/pages/SagasPage";
import ServicesPage from "@/pages/ServicesPage";
import TenantsPage from "@/pages/TenantsPage";
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
          path="/control"
          element={
            <RequireAdmin>
              <ControlHomePage />
            </RequireAdmin>
          }
        />
        <Route
          path="/tenants"
          element={
            <RequireAdmin>
              <TenantsPage />
            </RequireAdmin>
          }
        />
        <Route
          path="/plans"
          element={
            <RequireAdmin>
              <PlansPage />
            </RequireAdmin>
          }
        />
        <Route
          path="/policies"
          element={
            <RequireAdmin>
              <PoliciesPage />
            </RequireAdmin>
          }
        />
        <Route
          path="/catalog"
          element={
            <RequireAdmin>
              <CatalogPage />
            </RequireAdmin>
          }
        />
        <Route
          path="/services"
          element={
            <RequireAdmin>
              <ServicesPage />
            </RequireAdmin>
          }
        />
        <Route
          path="/commands"
          element={
            <RequireAdmin>
              <CommandsPage />
            </RequireAdmin>
          }
        />
        <Route
          path="/sagas"
          element={
            <RequireAdmin>
              <SagasPage />
            </RequireAdmin>
          }
        />
        <Route
          path="/audit"
          element={
            <RequireAdmin>
              <AuditPage />
            </RequireAdmin>
          }
        />
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
