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
import { extensionMessages } from "./extension-messages";

// ─── Language detection ─────────────────────────────────────────────────

const STORAGE_KEY = "ui_language";

/** All supported language codes, sorted for UI display */
export const SUPPORTED_LANGUAGES = Object.keys(translations).sort();

/**
 * Detect the user's language from the browser/Chrome settings
 * and map to our supported language codes.
 */
function detectLanguage(): string {
  const locale = navigator.language?.toLowerCase().replace("-", "_") ?? "en";

  if (locale in translations) return locale;

  const base = locale.split("_")[0];
  if (base === "zh") {
    if (
      locale.includes("tw") ||
      locale.includes("hk") ||
      locale.includes("hant")
    ) {
      return "zh_hant";
    }
    return "zh";
  }

  if (base in translations) return base;
  return "en";
}

/** Get the native display name for a language code */
export function getLanguageName(code: string): string {
  return translations[code]?.["current_language"] ?? code;
}

// ─── Context ────────────────────────────────────────────────────────────

type TranslateFn = (key: string, ...args: (string | number)[]) => string;

interface I18nContextValue {
  t: TranslateFn;
  language: string;
  setLanguage: (lang: string) => void;
}

const I18nContext = createContext<I18nContextValue | null>(null);

// ─── Provider ───────────────────────────────────────────────────────────

function buildTranslator(lang: string): TranslateFn {
  const messages: Record<string, string> = {
    ...translations.en,
    ...extensionMessages.en,
    ...translations[lang],
    ...extensionMessages[lang],
  };

  return function t(key: string, ...args: (string | number)[]): string {
    let text = messages[key] ?? key;
    for (const arg of args) {
      text = text.replace("%s", String(arg));
    }
    return text;
  };
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [language, setLanguageState] = useState<string | null>(null);

  // Load persisted language on mount
  useEffect(() => {
    chrome.storage.local.get(STORAGE_KEY).then((result) => {
      setLanguageState(
        (result[STORAGE_KEY] as string) ?? detectLanguage(),
      );
    });
  }, []);

  const setLanguage = useCallback((lang: string) => {
    setLanguageState(lang);
    chrome.storage.local.set({ [STORAGE_KEY]: lang });
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
