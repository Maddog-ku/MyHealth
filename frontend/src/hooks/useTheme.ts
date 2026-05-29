import { useCallback, useEffect, useState } from "react";

export type ThemeMode = "light" | "dark" | "system";

function resolveTheme(mode: ThemeMode) {
  if (mode === "system") {
    return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }
  return mode;
}

function applyTheme(mode: ThemeMode) {
  document.documentElement.dataset.theme = resolveTheme(mode);
}

export function useTheme() {
  const [mode, setMode] = useState<ThemeMode>(() => (localStorage.getItem("theme") as ThemeMode | null) ?? "system");

  useEffect(() => {
    applyTheme(mode);
    localStorage.setItem("theme", mode);
  }, [mode]);

  useEffect(() => {
    if (mode !== "system") return;
    const mql = window.matchMedia("(prefers-color-scheme: dark)");
    const onChange = () => applyTheme("system");
    mql.addEventListener("change", onChange);
    return () => mql.removeEventListener("change", onChange);
  }, [mode]);

  const setTheme = useCallback((next: ThemeMode) => setMode(next), []);
  return { mode, setTheme };
}
