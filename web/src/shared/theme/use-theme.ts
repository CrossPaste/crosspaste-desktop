import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  createElement,
  type ReactNode,
} from "react";

export type ThemeMode = "light" | "system" | "dark";

interface ThemeContextValue {
  themeMode: ThemeMode;
  isDark: boolean;
  setThemeMode: (mode: ThemeMode) => void;
}

const STORAGE_KEY = "ui_theme";

const ThemeContext = createContext<ThemeContextValue | null>(null);

function getSystemDark(): boolean {
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

function resolveIsDark(mode: ThemeMode): boolean {
  switch (mode) {
    case "light":
      return false;
    case "dark":
      return true;
    case "system":
      return getSystemDark();
  }
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [themeMode, setThemeModeState] = useState<ThemeMode | null>(null);
  const [isDark, setIsDark] = useState(false);

  // Load persisted theme on mount
  useEffect(() => {
    chrome.storage.local.get(STORAGE_KEY).then((result) => {
      const mode = (result[STORAGE_KEY] as ThemeMode) ?? "system";
      setThemeModeState(mode);
      setIsDark(resolveIsDark(mode));
    });
  }, []);

  // Listen for system theme changes when in "system" mode
  useEffect(() => {
    if (themeMode !== "system") return;

    const mq = window.matchMedia("(prefers-color-scheme: dark)");
    const handler = (e: MediaQueryListEvent) => setIsDark(e.matches);
    mq.addEventListener("change", handler);
    return () => mq.removeEventListener("change", handler);
  }, [themeMode]);

  // Apply dark class to document
  useEffect(() => {
    document.documentElement.classList.toggle("dark", isDark);
  }, [isDark]);

  const setThemeMode = useCallback((mode: ThemeMode) => {
    setThemeModeState(mode);
    setIsDark(resolveIsDark(mode));
    chrome.storage.local.set({ [STORAGE_KEY]: mode });
  }, []);

  // Don't render until theme is loaded
  if (themeMode === null) return null;

  return createElement(
    ThemeContext.Provider,
    { value: { themeMode, isDark, setThemeMode } },
    children,
  );
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error("useTheme must be used within ThemeProvider");
  return ctx;
}
