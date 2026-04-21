import {
  createContext,
  useContext,
  useState,
  useEffect,
  useMemo,
  useCallback,
  createElement,
  type ReactNode,
} from "react";
import { translations } from "./translations.generated";
import {
  buildTranslator,
  detectLanguage,
  LANGUAGE_STORAGE_KEY,
  type TranslateFn,
} from "./i18n-core";

// ─── Language detection ─────────────────────────────────────────────────

/** All supported language codes, sorted for UI display */
export const SUPPORTED_LANGUAGES = Object.keys(translations).sort();

/** Get the native display name for a language code */
export function getLanguageName(code: string): string {
  return translations[code]?.["current_language"] ?? code;
}

// ─── Context ────────────────────────────────────────────────────────────

interface I18nContextValue {
  t: TranslateFn;
  language: string;
  setLanguage: (lang: string) => void;
}

const I18nContext = createContext<I18nContextValue | null>(null);

// ─── Provider ───────────────────────────────────────────────────────────

export function I18nProvider({ children }: { children: ReactNode }) {
  const [language, setLanguageState] = useState<string | null>(null);

  // Load persisted language on mount
  useEffect(() => {
    chrome.storage.local.get(LANGUAGE_STORAGE_KEY).then((result) => {
      setLanguageState(
        (result[LANGUAGE_STORAGE_KEY] as string) ?? detectLanguage(),
      );
    });
  }, []);

  const setLanguage = useCallback((lang: string) => {
    setLanguageState(lang);
    chrome.storage.local.set({ [LANGUAGE_STORAGE_KEY]: lang });
  }, []);

  const t = useMemo(() => {
    return language ? buildTranslator(language) : buildTranslator("en");
  }, [language]);

  // Don't render until language is loaded
  if (!language) return null;

  return createElement(
    I18nContext.Provider,
    { value: { t, language, setLanguage } },
    children,
  );
}

// ─── Hooks ──────────────────────────────────────────────────────────────

/**
 * Returns the translation function `t(key, ...args)`.
 * Must be used inside `<I18nProvider>`.
 */
export function useI18n(): TranslateFn {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error("useI18n must be used within I18nProvider");
  return ctx.t;
}

/**
 * Returns language settings for the settings page.
 * Must be used inside `<I18nProvider>`.
 */
export function useLanguageSettings() {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error("useLanguageSettings must be used within I18nProvider");
  return {
    language: ctx.language,
    setLanguage: ctx.setLanguage,
  };
}
