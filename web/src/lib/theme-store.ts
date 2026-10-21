/* 主题优先级为 localStorage、系统偏好、亮色；须与 index.html 的防 FOUC 脚本保持一致。 */

import { create } from "zustand";

export const THEME_STORAGE_KEY = "jinguan-theme";

export type Theme = "light" | "dark";

function readStored(): Theme | null {
  try {
    const v = localStorage.getItem(THEME_STORAGE_KEY);
    return v === "light" || v === "dark" ? v : null;
  } catch {
    return null; // 无法读取时按未设置手动主题处理。
  }
}

function systemTheme(): Theme {
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function resolveTheme(): Theme {
  return readStored() ?? systemTheme();
}

function applyToDom(theme: Theme): void {
  document.documentElement.classList.toggle("dark", theme === "dark");
}

interface ThemeState {
  theme: Theme;
  toggle: () => void;
}

export const useTheme = create<ThemeState>((set, get) => ({
  theme: resolveTheme(),

  toggle: () => {
    const next: Theme = get().theme === "dark" ? "light" : "dark";
    try {
      localStorage.setItem(THEME_STORAGE_KEY, next);
    } catch {
      /* 保存失败时仅在当前会话生效。 */
    }
    applyToDom(next);
    set({ theme: next });
  },
}));

// 手动选择后不再跟随系统主题。
window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", (e) => {
  if (readStored() !== null) return;
  const next: Theme = e.matches ? "dark" : "light";
  applyToDom(next);
  useTheme.setState({ theme: next });
});
