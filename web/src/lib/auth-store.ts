/* 认证态(zustand):access 存内存,refresh 走 httpOnly cookie。
   挂载时 bootstrap() 静默续期恢复会话(页面刷新不掉登录);
   401 刷新失败经 api 层 sessionCleared 反向通知回落 guest(store 不被 api import)。 */

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
  booted: boolean; // 挂载续期已完成(无论成败),App 以此区分「加载中」与「未登录」
  bootstrap: () => Promise<void>;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

function decode(token: string): LoginUser | null {
  try {
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return {
      uid: Number(payload.uid),
      username: String(payload.sub),
      role: String(payload.role ?? "USER"),
    };
  } catch {
    return null;
  }
}

export const useAuth = create<AuthState>((set) => {
  // api 层 401 刷新失败清态 → 回落 guest(反向解耦:store 不被 api import)
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
        // 直连 fetch:清 cookie 即可,不走带 401 刷新链路的 api 包装
        await fetch("/auth/logout", { method: "POST" });
      } catch {
        // 网络失败也照常本地清态
      }
      clearAccessToken();
      set({ user: null });
    },
  };
});
