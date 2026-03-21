import { useMemo } from "react";
import { translations } from "./translations.generated";
import { extensionMessages } from "./extension-messages";

/**
 * Detect the user's language from the browser/Chrome settings
 * and map to our supported language codes.
 */
function detectLanguage(): string {
  const locale = navigator.language?.toLowerCase().replace("-", "_") ?? "en";

  // Exact match
  if (locale in translations) return locale;

  // Chinese variants
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

  // Base language match (e.g. "en_us" → "en")
  if (base in translations) return base;

  return "en";
}

/**
 * React hook that returns a translation function `t(key, ...args)`.
 *
 * Merges auto-generated desktop translations with Chrome-extension-specific
 * messages. Supports `%s` placeholder substitution.
 *
 * @example
 * const t = useI18n();
 * t("cancel")           // → "取消" (zh) / "Cancel" (en)
 * t("add_note_for", "MacBook") // → "为 MacBook 添加备注名称"
 */
export function useI18n() {
  return useMemo(() => {
    const lang = detectLanguage();

    // Merge: extension-specific keys override desktop keys
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
  }, []);
}
