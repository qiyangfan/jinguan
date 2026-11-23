/* 登录页只保留统一身份入口；未配置统一身份时不展示本地账密。 */

import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export default function LoginPage() {
  const [enabled, setEnabled] = useState<boolean | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetch("/auth/oidc/enabled")
      .then((resp) => (resp.ok ? resp.json() : { enabled: false }))
      .then((data: { enabled?: unknown }) => {
        if (!cancelled) setEnabled(data.enabled === true);
      })
      .catch(() => {
        if (!cancelled) setEnabled(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle as="h2" className="text-primary">
            锦官 · 控制面
          </CardTitle>
          <CardDescription>使用公司统一身份登录；锦官不保存密码。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {enabled === null && <p className="text-sm text-muted-foreground">正在检查登录配置…</p>}
          {enabled === true && (
            <Button asChild className="h-11 w-full">
              <a href="/auth/oidc/start">使用统一身份登录</a>
            </Button>
          )}
          {enabled === false && (
            <p className="text-sm text-destructive">
              统一身份未配置，请联系运维；统一身份不可用时无法新建登录。
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
