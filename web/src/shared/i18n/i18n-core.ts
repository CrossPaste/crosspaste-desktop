import { translations } from "./translations.generated";
import { extensionMessages } from "./extension-messages";

/** Storage key where the user-selected UI language is persisted. */
export const LANGUAGE_STORAGE_KEY = "ui_language";

export type TranslateFn = (key: string, ...args: (string | number)[]) => string;

/** Map a browser/navigator locale to a supported translation key. */
export function detectLanguage(): string {
  const locale = navigator.language?.toLowerCase().replace("-", "_") ?? "en";
  if (locale in translations) return locale;
  const base = locale.split("_")[0];
  if (base === "zh") {
    if (locale.includes("tw") || locale.includes("hk") || locale.includes("hant")) {
      return "zh_hant";
    }
    return "zh";
  }
  if (base in translations) return base;
  return "en";
}

/** Build a standalone translator for [lang] (falls back to English keys). */
export function buildTranslator(lang: string): TranslateFn {
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

/**
 * Read the user's active UI language — persisted preference if present,
 * otherwise browser detection. Safe to call from service-workers.
 */
export async function getActiveLanguage(): Promise<string> {
  const stored = await chrome.storage.local.get(LANGUAGE_STORAGE_KEY);
  return (stored[LANGUAGE_STORAGE_KEY] as string) || detectLanguage();
}

/**
 * Build a translator using the language persisted by the side panel UI,
 * falling back to browser detection. Safe to call from service-workers
 * (no React / DOM dependencies beyond `chrome.storage`).
 */
export async function buildTranslatorFromStorage(): Promise<TranslateFn> {
  return buildTranslator(await getActiveLanguage());
}
