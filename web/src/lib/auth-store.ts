/* 认证态：access 存内存，refresh 使用 httpOnly cookie；api 层通过回调清除 store，避免循环依赖。 */

import { create } from "zustand";

import {
  clearAccessToken,
  client,
  getAccessToken,
  onSessionCleared,
  refreshSession,
  setAccessToken,
  unwrap,
} from "./api";

export interface LoginUser {
  uid: number;
  username: string;
  role: string;
}

interface AuthState {
  user: LoginUser | null;
  booted: boolean;
  bootstrap: () => Promise<void>;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, password: string) => Promise<void>;
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
    return {
      uid: Number(claims.uid),
      username: String(claims.sub),
      role: typeof claims.role === "string" ? claims.role : "USER",
    };
  } catch {
    return null;
  }
}

export const useAuth = create<AuthState>((set) => {
  // API 层清除 token 时同步清除用户态。
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

    async login(username, password) {
      const out = await unwrap<{ token: string }>(
        client.POST("/auth/login", { body: { username, password } }),
      );
      setAccessToken(out.token);
      set({ user: decode(out.token) });
    },

    async register(username, password) {
      const out = await unwrap<{ token: string }>(
        client.POST("/auth/register", { body: { username, password } }),
      );
      setAccessToken(out.token);
      set({ user: decode(out.token) });
    },

    async logout() {
      try {
        // 避免 logout 请求进入 401 续期流程。
        await fetch("/auth/logout", { method: "POST" });
      } catch {
        // 本地认证态仍需清除。
      }
      clearAccessToken();
      set({ user: null });
    },
  };
});
