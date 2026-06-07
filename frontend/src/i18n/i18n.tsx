import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { dictionaries, type Lang } from "./dictionaries";

const STORAGE_KEY = "lang";

type I18nValue = {
  lang: Lang;
  setLang: (lang: Lang) => void;
  /** Translate a key; falls back to the key itself. Supports {placeholder} interpolation. */
  t: (key: string, vars?: Record<string, string | number>) => string;
};

const I18nContext = createContext<I18nValue | null>(null);

function initialLang(): Lang {
  const stored = localStorage.getItem(STORAGE_KEY);
  return stored === "en" || stored === "zh-TW" ? stored : "zh-TW";
}

function interpolate(template: string, vars?: Record<string, string | number>): string {
  if (!vars) return template;
  return template.replace(/\{(\w+)\}/g, (m, k) => (k in vars ? String(vars[k]) : m));
}

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(initialLang);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, lang);
    document.documentElement.lang = lang;
  }, [lang]);

  const setLang = useCallback((next: Lang) => setLangState(next), []);

  const t = useCallback(
    (key: string, vars?: Record<string, string | number>) =>
      interpolate(dictionaries[lang][key] ?? dictionaries["zh-TW"][key] ?? key, vars),
    [lang],
  );

  const value = useMemo<I18nValue>(() => ({ lang, setLang, t }), [lang, setLang, t]);
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nValue {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error("useI18n must be used within a LanguageProvider");
  return ctx;
}
