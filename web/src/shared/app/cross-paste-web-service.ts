import { AppUrls } from "./app-urls";
import { getActiveLanguage } from "@/shared/i18n/i18n-core";

/**
 * Mirrors the Kotlin [CrossPasteWebService] — resolves the locale path
 * segment of https://crosspaste.com so we can deep-link users to the
 * localized landing / download page.
 *
 * The desktop app fetches `/api/meta.json` and caches `localePathMap`;
 * we do the same here (lazy, in-memory) with a hard-coded fallback for
 * the cold-start case (no network or before refresh completes).
 */

interface WebLocale {
  code: string;
  label: string;
  path: string;
}

interface WebMeta {
  locales: WebLocale[];
}

let localePathMap: Record<string, string> = {};

async function refresh(): Promise<void> {
  try {
    const response = await fetch(`${AppUrls.homeUrl}/api/meta.json`, { cache: "no-cache" });
    if (!response.ok) return;
    const meta = (await response.json()) as WebMeta;
    localePathMap = Object.fromEntries(meta.locales.map((l) => [l.code, l.path]));
  } catch (e) {
    console.warn("[CrossPasteWebService] Failed to fetch web meta:", e);
  }
}

function resolveLocalePath(language: string): string {
  const map = localePathMap;
  if (Object.keys(map).length > 0) {
    return map[language] ?? map["en"] ?? "/en/";
  }
  // Fallback before meta.json loads, matching the desktop default.
  return language === "zh" ? "/" : "/en/";
}

export const CrossPasteWebService = {
  refresh,

  /** Build a localized URL like `https://crosspaste.com/en/download`. */
  getWebUrl(language: string, path = ""): string {
    return `${AppUrls.homeUrl}${resolveLocalePath(language)}${path}`;
  },

  /** Shortcut that reads the active UI language from storage. */
  async getLocalizedUrl(path = ""): Promise<string> {
    return this.getWebUrl(await getActiveLanguage(), path);
  },
};
