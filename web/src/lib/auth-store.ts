/* 认证态：登录只走虎符 OIDC；access 存内存，refresh 使用 httpOnly cookie。 */

import { create } from "zustand";

import {
  clearAccessToken,
  getAccessToken,
  onSessionCleared,
  refreshSession,
  setAccessToken,
} from "./api";

export interface LoginUser {
  subject: string;
  username: string;
  role: string;
}

interface AuthState {
  user: LoginUser | null;
  booted: boolean;
  bootstrap: () => Promise<void>;
  logout: () => Promise<void>;
}

function decode(token: string): LoginUser | null {
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;
    const claims = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/"))) as Record<
      string,
      unknown
    >;
    if (typeof claims.sub !== "string") return null;
    return {
      subject: claims.sub,
      username: typeof claims.username === "string" ? claims.username : claims.sub,
      role: typeof claims.role === "string" ? claims.role : "USER",
    };
  } catch {
    return null;
  }
}

export const useAuth = create<AuthState>((set) => {
  onSessionCleared(() => set({ user: null }));

  return {
    user: null,
    booted: false,

    async bootstrap() {
      if (useAuth.getState().booted) return;
      const ok = await refreshSession();
      const token = getAccessToken();
      set({ user: ok && token ? decode(token) : null, booted: true });
    },

    async logout() {
      try {
        await fetch("/auth/logout", { method: "POST" });
      } catch {
        // 本地认证态仍需清除。
      }
      clearAccessToken();
      set({ user: null });
      // 测试环境不执行整页导航；生产整页走 SSO 登出联动。
      if (import.meta.env.MODE !== "test") {
        window.location.assign("/auth/logout/sso");
      }
    },
  };
});

/** 只设置新增 token 后调用；页面刷新由 bootstrap 经 refresh 恢复。 */
export function acceptAccessToken(token: string): void {
  setAccessToken(token);
}
