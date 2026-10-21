/* 登录与注册共用表单。 */

import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { FormRow } from "@/components/form-row";
import { errorDetail } from "@/lib/api";
import { useAuth } from "@/lib/auth-store";

export default function LoginPage() {
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (mode === "login") {
        await login(username, password);
      } else {
        await register(username, password);
      }
      navigate("/wallet");
    } catch (err) {
      setError(errorDetail(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle as="h2" className="text-primary">
            锦官 · 控制面
          </CardTitle>
          <CardDescription>
            {mode === "login" ? "登录以管理钱包与计价" : "注册新账号;首个注册的用户将成为管理员"}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={(e) => void submit(e)} className="space-y-4">
            <FormRow label="用户名" htmlFor="username">
              <Input
                id="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                minLength={3}
              />
            </FormRow>
            <FormRow label="密码" htmlFor="password">
              <Input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={8}
              />
            </FormRow>
            {error && <p className="text-sm text-destructive">{error}</p>}
            <Button type="submit" className="h-11 w-full" disabled={busy}>
              {busy ? "提交中…" : mode === "login" ? "登录" : "注册并登录"}
            </Button>
            <Button
              type="button"
              variant="link"
              className="h-11 w-full"
              onClick={() => setMode(mode === "login" ? "register" : "login")}
            >
              {mode === "login" ? "没有账号?去注册" : "已有账号?去登录"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
