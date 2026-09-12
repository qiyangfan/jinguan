/* 认证态(zustand):authed/guest 两态;token 本地持有,过期由 401 清态回落 guest
   (api 层 sessionCleared 反向通知,store 不被 api import)。 */

import { create } from "zustand";
import { api, clearAccessToken, onSessionCleared, setAccessToken } from "./api";

export interface LoginUser {
  uid: number;
  username: string;
  role: string;
}

interface AuthState {
  user: LoginUser | null;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, password: string) => Promise<void>;
  logout: () => void;
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
  // api 层 401 清态 → 回落 guest(反向解耦:store 不被 api import)
  onSessionCleared(() => set({ user: null }));

  return {
    user: null,

    async login(username, password) {
      const out = await api.post<{ token: string }>("/auth/login", { username, password });
      setAccessToken(out.token);
      set({ user: decode(out.token) });
    },

    async register(username, password) {
      const out = await api.post<{ token: string }>("/auth/register", { username, password });
      setAccessToken(out.token);
      set({ user: decode(out.token) });
    },

    logout() {
      clearAccessToken();
      set({ user: null });
    },
  };
});
